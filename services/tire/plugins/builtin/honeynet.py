"""
Honeynet plugin — internal honeypot telemetry data.

Absorbs:
  - collectors/honeynet.py (collection)
  - scoring_rules.yaml sources.honeynet (scoring)
"""

import csv
import json
import logging
from pathlib import Path
from typing import Any

from models import EvidenceItem
from plugins.base import TIPlugin, PluginMetadata, PluginResult

logger = logging.getLogger(__name__)


class HoneynetPlugin(TIPlugin):
    """Self-contained honeynet/honeypot telemetry plugin."""

    @property
    def metadata(self) -> PluginMetadata:
        return PluginMetadata(
            name="honeynet",
            display_name="Honeynet",
            version="1.0.0",
            supported_types=["ip"],
            requires_api_key=False,
            api_key_env_var=None,
            rate_limit=None,
            priority=40,
            tags=["honeypot", "scanning"],
            description="Internal honeypot sensor telemetry",
        )

    async def query(self, observable: str, obs_type: str) -> PluginResult:
        data_path = Path(self.plugin_config.get("data_path", "data/honeynet.json"))

        if not data_path.exists():
            logger.debug(f"Honeynet data file not found: {data_path}")
            return self._empty_result()

        try:
            all_data = self._load_data(data_path)
            ip_data = all_data.get(observable, {})

            if not ip_data:
                return self._empty_result()

            standardized = self._parse_data(ip_data)
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
            logger.error(f"Honeynet error for {observable}: {e}")
            return self._error_result(str(e))

    def _load_data(self, data_path: Path) -> dict[str, Any]:
        """Load honeynet data from JSON or CSV file."""
        if data_path.suffix.lower() == ".json":
            with open(data_path, "r", encoding="utf-8") as f:
                return json.load(f)
        elif data_path.suffix.lower() == ".csv":
            data: dict[str, Any] = {}
            with open(data_path, "r", encoding="utf-8") as f:
                reader = csv.DictReader(f)
                for row in reader:
                    ip = row.get("ip")
                    if ip:
                        if ip not in data:
                            data[ip] = []
                        data[ip].append(row)
            return data
        else:
            raise ValueError(f"Unsupported file format: {data_path.suffix}")

    def _parse_data(self, ip_data: Any) -> dict[str, Any]:
        """Parse honeynet data for a given IP."""
        if isinstance(ip_data, list):
            total_hits = sum(
                int(entry.get("hits", 0))
                for entry in ip_data
                if isinstance(entry, dict)
            )
            ports: set[int] = set()
            time_distribution: dict[str, int] = {}

            for entry in ip_data:
                if isinstance(entry, dict):
                    if "port" in entry:
                        port_val = entry.get("port")
                        if port_val is not None:
                            ports.add(int(port_val))
                    timestamp = entry.get("timestamp", "unknown")
                    time_distribution[timestamp] = (
                        time_distribution.get(timestamp, 0) + 1
                    )

            timestamps = [
                entry.get("timestamp")
                for entry in ip_data
                if isinstance(entry, dict) and entry.get("timestamp")
            ]
            last_seen = max(timestamps) if timestamps else None

            return {
                "total_hits": total_hits,
                "unique_ports": len(ports),
                "ports_list": sorted(list(ports)),
                "time_distribution": time_distribution,
                "scan_fanout": len(ip_data),
                "last_seen": last_seen,
            }
        else:
            return {
                "total_hits": ip_data.get("hits", 0),
                "unique_ports": len(ip_data.get("ports", [])),
                "ports_list": ip_data.get("ports", []),
                "time_distribution": ip_data.get("time_distribution", {}),
                "scan_fanout": ip_data.get("scan_fanout", 0),
                "last_seen": ip_data.get("last_seen"),
            }

    def _empty_result(self) -> PluginResult:
        """Return result for IPs not in honeynet."""
        return PluginResult(
            source=self.metadata.name,
            ok=True,
            raw_data={
                "total_hits": 0,
                "unique_ports": 0,
                "ports_list": [],
                "time_distribution": {},
                "scan_fanout": 0,
                "last_seen": None,
            },
            normalized_data=None,
            evidence=[],
            error=None,
        )

    def _score(self, data: dict[str, Any]) -> list[EvidenceItem]:
        """Apply scoring rules (from scoring_rules.yaml sources.honeynet)."""
        evidence: list[EvidenceItem] = []
        total_hits = data.get("total_hits", 0)

        if total_hits > 10:
            score_delta = min(40, total_hits * 2)
            evidence.append(
                EvidenceItem(
                    source="honeynet",
                    category="reputation",
                    severity="high",
                    title="High honeynet activity",
                    detail=f"IP triggered honeynet {total_hits} times",
                    score_delta=score_delta,
                    confidence=0.5,
                )
            )
        elif total_hits > 0:
            evidence.append(
                EvidenceItem(
                    source="honeynet",
                    category="reputation",
                    severity="medium",
                    title="Honeynet detection",
                    detail="IP detected by honeynet sensors",
                    score_delta=15,
                    confidence=0.5,
                )
            )

        return evidence
