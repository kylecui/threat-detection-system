#!/usr/bin/env bash
#
# pg_failover.sh — PostgreSQL Primary/Standby Failover Tool
#
# Usage:
#   ./pg_failover.sh status              — Show replication status
#   ./pg_failover.sh promote <region>    — Promote standby to primary
#   ./pg_failover.sh slots               — List replication slots
#   ./pg_failover.sh lag                  — Show replication lag
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Default namespace prefix
NS_PREFIX="threat-detection"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

usage() {
    cat <<EOF
PostgreSQL Cross-Region Failover Tool

Usage:
  $0 status              Show replication status for all regions
  $0 promote <region>    Promote a standby to primary (region: east|west|cn)
  $0 slots               List replication slots on primary
  $0 lag                 Show replication lag on all standbys
  $0 help                Show this help message

Examples:
  $0 status
  $0 promote west
  $0 lag

EOF
}

# Get the namespace for a region
get_namespace() {
    local region="${1:-east}"
    echo "${NS_PREFIX}-${region}"
}

# Execute SQL on a region's postgres
pg_exec() {
    local region="${1}"
    local sql="${2}"
    local ns
    ns="$(get_namespace "$region")"

    kubectl exec -n "$ns" postgres-0 -- \
        psql -U threat_user -d threat_detection -t -A -c "$sql" 2>/dev/null
}

# Check if postgres pod exists in a region
check_region() {
    local region="${1}"
    local ns
    ns="$(get_namespace "$region")"

    if kubectl get pod -n "$ns" postgres-0 &>/dev/null; then
        return 0
    fi
    return 1
}

# Show replication status
cmd_status() {
    log_info "Checking PostgreSQL replication status across regions..."
    echo ""

    for region in east west cn; do
        local ns
        ns="$(get_namespace "$region")"

        if ! check_region "$region"; then
            echo "  [$region] ${RED}NOT DEPLOYED${NC} (namespace: $ns)"
            continue
        fi

        # Check if primary or standby
        local is_recovery
        is_recovery="$(pg_exec "$region" "SELECT pg_is_in_recovery();" 2>/dev/null || echo "error")"

        if [ "$is_recovery" = "f" ]; then
            echo -e "  [$region] ${GREEN}PRIMARY${NC} (namespace: $ns)"

            # Show connected standbys
            local standbys
            standbys="$(pg_exec "$region" "SELECT application_name, state, sent_lsn, write_lsn, flush_lsn, replay_lsn FROM pg_stat_replication;" 2>/dev/null || echo "")"
            if [ -n "$standbys" ]; then
                echo "    Connected standbys:"
                echo "    $standbys"
            else
                echo "    No standbys connected"
            fi
        elif [ "$is_recovery" = "t" ]; then
            echo -e "  [$region] ${YELLOW}STANDBY${NC} (namespace: $ns)"

            # Show lag
            local lag
            lag="$(pg_exec "$region" "SELECT CASE WHEN pg_last_wal_receive_lsn() IS NOT NULL THEN pg_wal_lsn_diff(pg_last_wal_receive_lsn(), pg_last_wal_replay_lsn())::text ELSE 'N/A' END;" 2>/dev/null || echo "N/A")"
            echo "    Replay lag (bytes): $lag"
        else
            echo -e "  [$region] ${RED}UNKNOWN${NC} (namespace: $ns) — Cannot connect"
        fi
        echo ""
    done
}

# Promote a standby to primary
cmd_promote() {
    local region="${1:-}"

    if [ -z "$region" ]; then
        log_error "Region is required. Usage: $0 promote <region>"
        exit 1
    fi

    local ns
    ns="$(get_namespace "$region")"

    if ! check_region "$region"; then
        log_error "Region '$region' is not deployed (namespace: $ns)"
        exit 1
    fi

    # Check if it's actually a standby
    local is_recovery
    is_recovery="$(pg_exec "$region" "SELECT pg_is_in_recovery();")"

    if [ "$is_recovery" = "f" ]; then
        log_warn "Region '$region' is already a PRIMARY. No promotion needed."
        exit 0
    fi

    log_warn "About to PROMOTE region '$region' standby to PRIMARY."
    log_warn "This is a DESTRUCTIVE operation. The old primary must be reconfigured as standby."
    echo ""
    read -p "Are you sure? (yes/no): " confirm

    if [ "$confirm" != "yes" ]; then
        log_info "Cancelled."
        exit 0
    fi

    log_info "Promoting $region standby to primary..."

    kubectl exec -n "$ns" postgres-0 -- \
        pg_ctl promote -D /var/lib/postgresql/data/pgdata

    sleep 3

    # Verify promotion
    is_recovery="$(pg_exec "$region" "SELECT pg_is_in_recovery();")"
    if [ "$is_recovery" = "f" ]; then
        log_info "SUCCESS: Region '$region' is now PRIMARY."
        log_warn "IMPORTANT: Update the old primary to standby mode and reconfigure replication."
    else
        log_error "Promotion may have failed. Check logs: kubectl logs -n $ns postgres-0"
    fi
}

# List replication slots
cmd_slots() {
    log_info "Listing replication slots..."
    echo ""

    for region in east west cn; do
        if ! check_region "$region"; then
            continue
        fi

        local is_recovery
        is_recovery="$(pg_exec "$region" "SELECT pg_is_in_recovery();" 2>/dev/null || echo "error")"

        if [ "$is_recovery" = "f" ]; then
            echo "  [$region] PRIMARY — Replication slots:"
            local slots
            slots="$(pg_exec "$region" "SELECT slot_name, slot_type, active, restart_lsn FROM pg_replication_slots;" 2>/dev/null || echo "  (none)")"
            echo "    $slots"
            echo ""
        fi
    done
}

# Show replication lag
cmd_lag() {
    log_info "Checking replication lag..."
    echo ""

    for region in east west cn; do
        if ! check_region "$region"; then
            continue
        fi

        local is_recovery
        is_recovery="$(pg_exec "$region" "SELECT pg_is_in_recovery();" 2>/dev/null || echo "error")"

        if [ "$is_recovery" = "t" ]; then
            local lag_bytes lag_time
            lag_bytes="$(pg_exec "$region" "SELECT pg_wal_lsn_diff(pg_last_wal_receive_lsn(), pg_last_wal_replay_lsn());" 2>/dev/null || echo "N/A")"
            lag_time="$(pg_exec "$region" "SELECT now() - pg_last_xact_replay_timestamp();" 2>/dev/null || echo "N/A")"

            echo "  [$region] STANDBY"
            echo "    Lag (bytes): $lag_bytes"
            echo "    Lag (time):  $lag_time"
            echo ""
        fi
    done
}

# Main
case "${1:-help}" in
    status)  cmd_status ;;
    promote) cmd_promote "${2:-}" ;;
    slots)   cmd_slots ;;
    lag)     cmd_lag ;;
    help|*)  usage ;;
esac
