"""
Reverse DNS plugin — PTR record resolution.

Absorbs:
  - collectors/reverse_dns.py (collection)
  - normalizers/ip_normalizer.py _extract_reverse_dns_data (profile normalization)
  - No scoring rules
"""

import asyncio
import logging
import socket
from typing import Any

from models import EvidenceItem
from plugins.base import TIPlugin, PluginMetadata, PluginResult

logger = logging.getLogger(__name__)


class ReverseDNSPlugin(TIPlugin):
    """Self-contained reverse DNS lookup plugin."""

    @property
    def metadata(self) -> PluginMetadata:
        return PluginMetadata(
            name="reverse_dns",
            display_name="Reverse DNS",
            version="1.0.0",
            supported_types=["ip"],
            requires_api_key=False,
            api_key_env_var=None,
            rate_limit=None,
            priority=5,
            tags=["dns", "hostname"],
            description="PTR record resolution for IP addresses",
        )

    async def query(self, observable: str, obs_type: str) -> PluginResult:
        try:
            loop = asyncio.get_event_loop()
            rdns_data = await loop.run_in_executor(
                None, self._sync_reverse_dns, observable
            )

            # Profile-level normalization
            normalized = self._extract_profile_data(rdns_data)

            return PluginResult(
                source=self.metadata.name,
                ok=True,
                raw_data=rdns_data,
                normalized_data=normalized,
                evidence=[],  # No scoring rules for reverse DNS
                error=None,
            )

        except Exception as e:
            error_msg = f"Reverse DNS lookup failed: {e}"
            logger.warning(error_msg)
            return self._error_result(error_msg)

    def _sync_reverse_dns(self, ip: str) -> dict[str, Any]:
        """Synchronous reverse DNS lookup."""
        try:
            reversed_ip = ".".join(reversed(ip.split(".")))
            lookup_name = f"{reversed_ip}.in-addr.arpa"

            answers = socket.gethostbyaddr(ip)
            hostname = answers[0]
            aliases = answers[1] if len(answers) > 1 else []

            return {
                "ip": ip,
                "hostname": hostname,
                "aliases": aliases,
                "lookup_name": lookup_name,
            }

        except socket.herror:
            return {
                "ip": ip,
                "hostname": None,
                "aliases": [],
                "lookup_name": f"{'.'.join(reversed(ip.split('.')))}.in-addr.arpa",
                "error": "No PTR record found",
            }
        except Exception as e:
            return {"ip": ip, "hostname": None, "aliases": [], "error": str(e)}

    def _extract_profile_data(self, rdns_data: dict[str, Any]) -> dict[str, Any]:
        """Extract profile-level fields for IPProfile normalization.

        These will be applied by QueryEngine._apply_profile_data():
            profile.rdns      = normalized_data["rdns"]
            profile.hostnames = normalized_data["hostnames"]
        """
        normalized: dict[str, Any] = {}

        if rdns_data.get("hostname"):
            normalized["rdns"] = [rdns_data["hostname"]]

        if rdns_data.get("aliases"):
            normalized["hostnames"] = rdns_data["aliases"]

        return normalized if normalized else {}
