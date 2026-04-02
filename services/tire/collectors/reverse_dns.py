"""
Reverse DNS collector for IP to hostname resolution.
"""

import logging
import socket
import asyncio
from typing import Dict, Any, List
from .base import BaseCollector

logger = logging.getLogger(__name__)


class ReverseDNSCollector(BaseCollector):
    """Collector for reverse DNS lookups."""

    def __init__(self):
        super().__init__("reverse_dns")
        # No API key needed for DNS lookups

    async def query(self, observable: str) -> Dict[str, Any]:
        """
        Perform reverse DNS lookup for IP address.

        Args:
            observable: IP address to resolve

        Returns:
            Standardized response dict
        """
        try:
            # Perform reverse DNS lookup in a thread pool to avoid blocking
            loop = asyncio.get_event_loop()
            rdns_result = await loop.run_in_executor(
                None, self._sync_reverse_dns, observable
            )

            return {"source": self.name, "ok": True, "data": rdns_result, "error": None}

        except Exception as e:
            error_msg = f"Reverse DNS lookup failed: {str(e)}"
            logger.warning(error_msg)
            return {"source": self.name, "ok": False, "data": None, "error": error_msg}

    def _sync_reverse_dns(self, ip: str) -> Dict[str, Any]:
        """Synchronous reverse DNS lookup."""
        try:
            # Convert IP to reverse lookup format
            reversed_ip = ".".join(reversed(ip.split(".")))
            lookup_name = f"{reversed_ip}.in-addr.arpa"

            # Perform PTR lookup
            answers = socket.gethostbyaddr(ip)
            hostname = answers[0]
            aliases = answers[1] if len(answers) > 1 else []

            return {
                "ip": ip,
                "hostname": hostname,
                "aliases": aliases,
                "lookup_name": lookup_name,
            }

        except socket.herror as e:
            # No PTR record found
            return {
                "ip": ip,
                "hostname": None,
                "aliases": [],
                "lookup_name": f"{'.'.join(reversed(ip.split('.')))}.in-addr.arpa",
                "error": "No PTR record found",
            }
        except Exception as e:
            return {"ip": ip, "hostname": None, "aliases": [], "error": str(e)}
