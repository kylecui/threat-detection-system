"""
Internal flow collector for internal network traffic data.
"""

import logging
import json
import csv
from typing import Dict, Any, Optional, List
from pathlib import Path
from .base import BaseCollector

logger = logging.getLogger(__name__)


class InternalFlowCollector(BaseCollector):
    """Collector for internal network flow data."""

    name = "internal_flow"

    def __init__(self, data_path: str = "data/internal_flows.json"):
        super().__init__("internal_flow")
        self.data_path = Path(data_path)

    async def query(self, observable: str) -> Dict[str, Any]:
        """Query internal flow data for IP."""
        if not self.data_path.exists():
            logger.debug(f"Internal flow data file not found: {self.data_path}")
            return self._empty_response()

        try:
            # Load flow data
            data = self._load_data()
            ip_data = data.get(observable, [])

            if not ip_data:
                return self._empty_response()

            return self._parse_response(ip_data)

        except Exception as e:
            logger.error(f"Internal flow error for {observable}: {e}")
            return self._error_response(str(e))

    def _load_data(self) -> Dict[str, Any]:
        """Load internal flow data from file."""
        if self.data_path.suffix.lower() == ".json":
            with open(self.data_path, "r", encoding="utf-8") as f:
                return json.load(f)
        elif self.data_path.suffix.lower() == ".csv":
            data = {}
            with open(self.data_path, "r", encoding="utf-8") as f:
                reader = csv.DictReader(f)
                for row in reader:
                    src_ip = row.get("src_ip")
                    dst_ip = row.get("dst_ip")

                    # Index by both src and dst
                    for ip in [src_ip, dst_ip]:
                        if ip:
                            if ip not in data:
                                data[ip] = []
                            data[ip].append(row)
            return data
        else:
            raise ValueError(f"Unsupported file format: {self.data_path.suffix}")

    def _parse_response(self, flows: List[Dict[str, Any]]) -> Dict[str, Any]:
        """Parse flow data for IP."""
        # Aggregate flow statistics
        total_sessions = len(flows)
        protocols = set()
        ports = set()
        total_bytes = 0
        total_packets = 0

        first_seen = None
        last_seen = None

        for flow in flows:
            protocols.add(flow.get("protocol", "").lower())
            if "src_port" in flow:
                ports.add(int(flow.get("src_port")))
            if "dst_port" in flow:
                ports.add(int(flow.get("dst_port")))

            total_bytes += int(flow.get("bytes", 0))
            total_packets += int(flow.get("packets", 0))

            # Track timestamps
            timestamp = (
                flow.get("timestamp") or flow.get("first_seen") or flow.get("last_seen")
            )
            if timestamp:
                if not first_seen or timestamp < first_seen:
                    first_seen = timestamp
                if not last_seen or timestamp > last_seen:
                    last_seen = timestamp

        return {
            "source": self.name,
            "ok": True,
            "data": {
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
            },
        }

    def _empty_response(self) -> Dict[str, Any]:
        """Return empty response for IPs not in flow data."""
        return {
            "source": self.name,
            "ok": True,
            "data": {
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
        }

    def _error_response(self, error: str) -> Dict[str, Any]:
        """Return error response."""
        return {"source": self.name, "ok": False, "data": None, "error": error}
