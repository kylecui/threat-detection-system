#!/bin/bash

###############################################################################
# Docker Build & Deploy Master Script
# Purpose: Intelligent rebuild system to prevent cache-related issues
# Usage: bash scripts/rebuild.sh [SERVICE] [FULL]
# Examples:
#   bash scripts/rebuild.sh alert-management          # Quick rebuild
#   bash scripts/rebuild.sh alert-management full     # Full rebuild (no cache)
#   bash scripts/rebuild.sh                           # Rebuilds all services
###############################################################################

set -e  # Exit on any error

# ============================================================================
# Configuration
# ============================================================================

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Script paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DOCKER_DIR="$PROJECT_ROOT/docker"

# Services to manage
VALID_SERVICES=("data-ingestion" "stream-processing" "threat-assessment" \
                "alert-management" "customer-management" "api-gateway")

# ============================================================================
# Functions
# ============================================================================

log_info() {
    echo -e "${BLUE}ℹ️  ${NC}$1"
}

log_success() {
    echo -e "${GREEN}✅${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}⚠️  ${NC}$1"
}

log_error() {
    echo -e "${RED}❌${NC} $1"
}

log_header() {
    echo ""
    echo -e "${CYAN}╔════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║ ${NC}$1"
    echo -e "${CYAN}╚════════════════════════════════════════╝${NC}"
    echo ""
}

validate_service() {
    local service=$1
    if [[ ! " ${VALID_SERVICES[@]} " =~ " ${service} " ]]; then
        log_error "Invalid service: $service"
        echo "Valid services: ${VALID_SERVICES[*]}"
        exit 1
    fi
}

# ============================================================================
# Main Script
# ============================================================================

# Parse arguments
SERVICE="${1:-all}"
BUILD_MODE="${2:-quick}"  # quick or full

# Validate service
if [ "$SERVICE" != "all" ]; then
    validate_service "$SERVICE"
fi

log_header "Docker Build & Deploy System"

# Step 1: Pre-flight checks
log_info "Running pre-flight checks..."
if ! command -v docker &> /dev/null; then
    log_error "Docker not found"
    exit 1
fi
if ! command -v docker-compose &> /dev/null; then
    log_error "Docker Compose not found"
    exit 1
fi
log_success "Docker tools verified"

# Step 2: Clean artifacts if full rebuild
if [ "$BUILD_MODE" = "full" ]; then
    log_info "Full rebuild requested - cleaning local artifacts..."
    if [ "$SERVICE" = "all" ]; then
        for svc in "${VALID_SERVICES[@]}"; do
            if [ -d "$PROJECT_ROOT/services/$svc/target" ]; then
                rm -rf "$PROJECT_ROOT/services/$svc/target/"
                log_success "Cleaned $svc"
            fi
        done
    else
        if [ -d "$PROJECT_ROOT/services/$SERVICE/target" ]; then
            rm -rf "$PROJECT_ROOT/services/$SERVICE/target/"
            log_success "Cleaned $SERVICE"
        fi
    fi
fi

# Step 3: Maven builds
log_info "Building with Maven..."
if [ "$SERVICE" = "all" ]; then
    for svc in "${VALID_SERVICES[@]}"; do
        if [ -d "$PROJECT_ROOT/services/$svc" ]; then
            log_info "Building $svc..."
            cd "$PROJECT_ROOT/services/$svc"
            if ! mvn clean package -DskipTests > /tmp/maven-$svc.log 2>&1; then
                log_error "Maven build failed for $svc"
                tail -20 /tmp/maven-$svc.log
                exit 1
            fi
            log_success "$svc built"
        fi
    done
else
    cd "$PROJECT_ROOT/services/$SERVICE"
    if ! mvn clean package -DskipTests > /tmp/maven-$SERVICE.log 2>&1; then
        log_error "Maven build failed for $SERVICE"
        tail -20 /tmp/maven-$SERVICE.log
        exit 1
    fi
    log_success "$SERVICE built"
fi

# Step 4: System cleanup if full rebuild
if [ "$BUILD_MODE" = "full" ]; then
    log_info "Cleaning Docker layer cache (--no-cache rebuild)..."
    docker system prune -f > /dev/null 2>&1 || true
    log_success "Docker cache cleaned"
fi

# Step 5: Docker builds
cd "$DOCKER_DIR"

log_info "Building Docker images..."
if [ "$SERVICE" = "all" ]; then
    # Build all services
    for svc in "${VALID_SERVICES[@]}"; do
        log_info "Building Docker image for $svc..."
        if [ "$BUILD_MODE" = "full" ]; then
            docker-compose build --no-cache $svc
        else
            docker-compose build $svc
        fi
        log_success "$svc Docker image built"
    done
else
    log_info "Building Docker image for $SERVICE..."
    if [ "$BUILD_MODE" = "full" ]; then
        docker-compose build --no-cache $SERVICE
    else
        docker-compose build $SERVICE
    fi
    log_success "$SERVICE Docker image built"
fi

# Step 6: Container restart
log_info "Restarting containers..."
cd "$DOCKER_DIR"

if [ "$SERVICE" = "all" ]; then
    log_warning "Restarting all services - this may take a moment..."
    docker-compose stop || true
    docker-compose up -d
    log_success "All services restarted"
else
    log_info "Restarting $SERVICE..."
    docker-compose stop $SERVICE || true
    docker-compose rm -f $SERVICE || true
    docker-compose up -d $SERVICE
    log_success "$SERVICE restarted"
fi

# Step 7: Wait for services
log_info "Waiting for services to become healthy..."
sleep 10

# Step 8: Health checks
log_info "Performing health checks..."
if [ "$SERVICE" = "all" ]; then
    for svc in "${VALID_SERVICES[@]}"; do
        HEALTH=$(docker-compose ps $svc 2>/dev/null | grep -E "healthy|Up" || true)
        if [ -n "$HEALTH" ]; then
            log_success "$svc is running"
        else
            log_warning "$svc status unclear"
            docker-compose logs $svc | tail -5
        fi
    done
else
    HEALTH=$(docker-compose ps $SERVICE 2>/dev/null | grep -E "healthy|Up" || true)
    if [ -n "$HEALTH" ]; then
        log_success "$SERVICE is healthy"
    else
        log_warning "$SERVICE status unclear"
        docker-compose logs $SERVICE | tail -10
    fi
fi

# ============================================================================
# Summary
# ============================================================================

echo ""
log_header "Rebuild Complete"

if [ "$SERVICE" = "all" ]; then
    log_success "All services have been rebuilt"
else
    log_success "$SERVICE has been rebuilt"
fi

if [ "$BUILD_MODE" = "full" ]; then
    log_info "Mode: FULL rebuild (--no-cache, fresh from JAR)"
else
    log_info "Mode: QUICK rebuild (using Docker cache)"
fi

echo ""
log_info "Next steps:"
echo "  1. Check service logs: docker-compose logs -f [service]"
echo "  2. Run tests: bash scripts/test_backend_api_happy_path.sh"
echo "  3. Verify API: curl http://localhost:8888/actuator/health"
echo ""

log_success "Build script completed successfully!"
