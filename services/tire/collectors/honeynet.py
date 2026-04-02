"""
Honeynet collector for internal honeypot data.
"""

import logging
import json
import csv
import os
from typing import Dict, Any, Optional, List
from pathlib import Path
from .base import BaseCollector

logger = logging.getLogger(__name__)


class HoneynetCollector(BaseCollector):
    """Collector for honeynet/honeypot telemetry data."""

    name = "honeynet"

    def __init__(self, data_path: str = "data/honeynet.json"):
        super().__init__("honeynet")
        self.data_path = Path(data_path)

    async def query(self, observable: str) -> Dict[str, Any]:
        """Query honeynet data for IP."""
        if not self.data_path.exists():
            logger.debug(f"Honeynet data file not found: {self.data_path}")
            return self._empty_response()

        try:
            # Load honeynet data
            data = self._load_data()
            ip_data = data.get(observable, {})

            if not ip_data:
                return self._empty_response()

            return self._parse_response(ip_data)

        except Exception as e:
            logger.error(f"Honeynet error for {observable}: {e}")
            return self._error_response(str(e))

    def _load_data(self) -> Dict[str, Any]:
        """Load honeynet data from file."""
        if self.data_path.suffix.lower() == ".json":
            with open(self.data_path, "r", encoding="utf-8") as f:
                return json.load(f)
        elif self.data_path.suffix.lower() == ".csv":
            data = {}
            with open(self.data_path, "r", encoding="utf-8") as f:
                reader = csv.DictReader(f)
                for row in reader:
                    ip = row.get("ip")
                    if ip:
                        if ip not in data:
                            data[ip] = []
                        data[ip].append(row)
            return data
        else:
            raise ValueError(f"Unsupported file format: {self.data_path.suffix}")

    def _parse_response(self, ip_data: Dict[str, Any]) -> Dict[str, Any]:
        """Parse honeynet data for IP."""
        # If data is a list (from CSV), aggregate
        if isinstance(ip_data, list):
            # Aggregate multiple entries
            total_hits = sum(
                int(entry.get("hits", 0))
                for entry in ip_data
                if isinstance(entry, dict)
            )
            ports = set()
            time_distribution = {}

            for entry in ip_data:
                if isinstance(entry, dict):
                    if "port" in entry:
                        ports.add(int(entry.get("port", 0)))
                    # Simple time distribution (could be enhanced)
                    timestamp = entry.get("timestamp", "unknown")
                    time_distribution[timestamp] = (
                        time_distribution.get(timestamp, 0) + 1
                    )

            return {
                "source": self.name,
                "ok": True,
                "data": {
                    "total_hits": total_hits,
                    "unique_ports": len(ports),
                    "ports_list": sorted(list(ports)),
                    "time_distribution": time_distribution,
                    "scan_fanout": len(ip_data),  # Number of distinct scan events
                    "last_seen": max(
                        (
                            entry.get("timestamp")
                            for entry in ip_data
                            if isinstance(entry, dict) and entry.get("timestamp")
                        ),
                        default=None,
                    ),
                },
            }
        else:
            # Single entry dict
            return {
                "source": self.name,
                "ok": True,
                "data": {
                    "total_hits": ip_data.get("hits", 0),
                    "unique_ports": len(ip_data.get("ports", [])),
                    "ports_list": ip_data.get("ports", []),
                    "time_distribution": ip_data.get("time_distribution", {}),
                    "scan_fanout": ip_data.get("scan_fanout", 0),
                    "last_seen": ip_data.get("last_seen"),
                },
            }

    def _empty_response(self) -> Dict[str, Any]:
        """Return empty response for IPs not in honeynet."""
        return {
            "source": self.name,
            "ok": True,
            "data": {
                "total_hits": 0,
                "unique_ports": 0,
                "ports_list": [],
                "time_distribution": {},
                "scan_fanout": 0,
                "last_seen": None,
            },
        }

    def _error_response(self, error: str) -> Dict[str, Any]:
        """Return error response."""
        return {"source": self.name, "ok": False, "data": None, "error": error}
