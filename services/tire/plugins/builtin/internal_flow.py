"""
Internal flow plugin — internal network traffic telemetry.

Absorbs:
  - collectors/internal_flow.py (collection)
  - scoring_rules.yaml sources.internal_flow (scoring)
"""

import csv
import json
import logging
from pathlib import Path
from typing import Any

from models import EvidenceItem
from plugins.base import TIPlugin, PluginMetadata, PluginResult

logger = logging.getLogger(__name__)


class InternalFlowPlugin(TIPlugin):
    """Self-contained internal network flow data plugin."""

    @property
    def metadata(self) -> PluginMetadata:
        return PluginMetadata(
            name="internal_flow",
            display_name="Internal Flow",
            version="1.0.0",
            supported_types=["ip"],
            requires_api_key=False,
            api_key_env_var=None,
            rate_limit=None,
            priority=40,
            tags=["internal", "network_flow"],
            description="Internal network traffic flow analysis",
        )

    async def query(self, observable: str, obs_type: str) -> PluginResult:
        data_path = Path(
            self.plugin_config.get("data_path", "data/internal_flows.json")
        )

        if not data_path.exists():
            logger.debug(f"Internal flow data file not found: {data_path}")
            return self._empty_result()

        try:
            all_data = self._load_data(data_path)
            ip_data = all_data.get(observable, [])

            if not ip_data:
                return self._empty_result()

            standardized = self._parse_flows(ip_data)
            evidence = self._score(standardized)

            return PluginResult(
                source=self.metadata.name,
                ok=True,
                raw_data=standardized,
                normalized_data=None,
                evidence=evidence,
                error=None,
            )

        except Exception as e:
            logger.error(f"Internal flow error for {observable}: {e}")
            return self._error_result(str(e))

    def _load_data(self, data_path: Path) -> dict[str, Any]:
        """Load flow data from JSON or CSV file."""
        if data_path.suffix.lower() == ".json":
            with open(data_path, "r", encoding="utf-8") as f:
                return json.load(f)
        elif data_path.suffix.lower() == ".csv":
            data: dict[str, Any] = {}
            with open(data_path, "r", encoding="utf-8") as f:
                reader = csv.DictReader(f)
                for row in reader:
                    src_ip = row.get("src_ip")
                    dst_ip = row.get("dst_ip")
                    for ip in [src_ip, dst_ip]:
                        if ip:
                            if ip not in data:
                                data[ip] = []
                            data[ip].append(row)
            return data
        else:
            raise ValueError(f"Unsupported file format: {data_path.suffix}")

    def _parse_flows(self, flows: list[dict[str, Any]]) -> dict[str, Any]:
        """Aggregate flow statistics."""
        total_sessions = len(flows)
        protocols: set[str] = set()
        ports: set[int] = set()
        total_bytes = 0
        total_packets = 0
        first_seen = None
        last_seen = None

        for flow in flows:
            protocols.add(flow.get("protocol", "").lower())

            src_port = flow.get("src_port")
            if src_port is not None:
                ports.add(int(src_port))
            dst_port = flow.get("dst_port")
            if dst_port is not None:
                ports.add(int(dst_port))

            total_bytes += int(flow.get("bytes", 0))
            total_packets += int(flow.get("packets", 0))

            timestamp = (
                flow.get("timestamp") or flow.get("first_seen") or flow.get("last_seen")
            )
            if timestamp:
                if not first_seen or timestamp < first_seen:
                    first_seen = timestamp
                if not last_seen or timestamp > last_seen:
                    last_seen = timestamp

        return {
            "session_count": total_sessions,
            "protocols": sorted(list(protocols)),
            "ports": sorted(list(ports)),
            "total_bytes": total_bytes,
            "total_packets": total_packets,
            "first_seen": first_seen,
            "last_seen": last_seen,
            "avg_bytes_per_session": total_bytes / total_sessions
            if total_sessions > 0
            else 0,
            "avg_packets_per_session": total_packets / total_sessions
            if total_sessions > 0
            else 0,
        }

    def _empty_result(self) -> PluginResult:
        """Return result for IPs not in flow data."""
        return PluginResult(
            source=self.metadata.name,
            ok=True,
            raw_data={
                "session_count": 0,
                "protocols": [],
                "ports": [],
                "total_bytes": 0,
                "total_packets": 0,
                "first_seen": None,
                "last_seen": None,
                "avg_bytes_per_session": 0,
                "avg_packets_per_session": 0,
            },
            normalized_data=None,
            evidence=[],
            error=None,
        )

    def _score(self, data: dict[str, Any]) -> list[EvidenceItem]:
        """Apply scoring rules (from scoring_rules.yaml sources.internal_flow)."""
        evidence: list[EvidenceItem] = []
        session_count = data.get("session_count", 0)

        if session_count > 100:
            evidence.append(
                EvidenceItem(
                    source="internal_flow",
                    category="reputation",
                    severity="low",
                    title="High internal connectivity",
                    detail=f"IP has {session_count} internal sessions - likely legitimate",
                    score_delta=-10,
                    confidence=0.5,
                )
            )
        elif session_count > 10:
            evidence.append(
                EvidenceItem(
                    source="internal_flow",
                    category="reputation",
                    severity="low",
                    title="Moderate internal activity",
                    detail=f"IP has {session_count} internal sessions",
                    score_delta=-5,
                    confidence=0.5,
                )
            )

        return evidence
