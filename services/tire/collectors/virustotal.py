"""
VirusTotal IP collector.
"""

import logging
from typing import Dict, Any, Optional
import httpx
from app.config import settings
from .base import BaseCollector

logger = logging.getLogger(__name__)


class VirusTotalCollector(BaseCollector):
    """Collector for VirusTotal IP intelligence."""

    name = "virustotal"

    def __init__(self):
        super().__init__("virustotal")
        self.api_key = settings.vt_api_key
        self.base_url = "https://www.virustotal.com/api/v3"

    async def query(self, observable: str) -> Dict[str, Any]:
        """Query VirusTotal for IP information."""
        if not self.api_key:
            logger.warning("VirusTotal API key not configured, skipping")
            return self._error_response("API key not configured")

        try:
            headers = {"x-apikey": self.api_key, "accept": "application/json"}

            logger.info(f"VirusTotal querying {observable}")
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                # 1) Base IP report
                url = f"{self.base_url}/ip_addresses/{observable}"
                response = await client.get(url, headers=headers)
                response.raise_for_status()
                data = response.json()

                # 2) Passive DNS resolutions (separate endpoint)
                resolutions = await self._fetch_resolutions(client, headers, observable)

                result = self._parse_response(data, resolutions)
                logger.info(
                    f"VirusTotal result for {observable}: "
                    f"malicious={result['data']['malicious_count']}, "
                    f"suspicious={result['data']['suspicious_count']}, "
                    f"resolutions={len(result['data']['related_domains'])}"
                )
                return result

        except httpx.HTTPStatusError as e:
            if e.response.status_code == 404:
                logger.info(f"VirusTotal: IP {observable} not found (404)")
                return self._empty_response()
            logger.error(
                f"VirusTotal HTTP error for {observable}: HTTP {e.response.status_code}"
            )
            return self._error_response(f"HTTP {e.response.status_code}")
        except Exception as e:
            logger.error(f"VirusTotal error for {observable}: {e}")
            return self._error_response(str(e))

    async def _fetch_resolutions(
        self, client: httpx.AsyncClient, headers: Dict[str, str], ip: str
    ) -> list:
        """Fetch passive DNS resolutions for an IP.  Best-effort — never fails the parent query."""
        try:
            url = f"{self.base_url}/ip_addresses/{ip}/resolutions"
            resp = await client.get(url, headers=headers, params={"limit": 20})
            resp.raise_for_status()
            items = resp.json().get("data", [])
            domains = []
            for item in items:
                host = (item.get("attributes") or {}).get("host_name")
                if host:
                    domains.append(host)
            logger.info(f"VirusTotal resolutions for {ip}: {len(domains)} domains")
            return domains
        except Exception as e:
            logger.warning(f"VirusTotal resolutions fetch failed for {ip}: {e}")
            return []

    def _parse_response(
        self, data: Dict[str, Any], resolutions: Optional[list] = None
    ) -> Dict[str, Any]:
        """Parse VirusTotal API response."""
        attributes = data.get("data", {}).get("attributes", {})

        # Extract analysis stats
        last_analysis_stats = attributes.get("last_analysis_stats", {})
        malicious = last_analysis_stats.get("malicious", 0)
        suspicious = last_analysis_stats.get("suspicious", 0)
        harmless = last_analysis_stats.get("harmless", 0)
        undetected = last_analysis_stats.get("undetected", 0)

        # Calculate reputation (malicious ratio)
        total = malicious + suspicious + harmless + undetected
        reputation = (malicious + suspicious) / total if total > 0 else 0

        # Extract owner info
        as_owner = attributes.get("as_owner", "")

        # Extract tags
        tags = attributes.get("tags", [])

        # Use resolutions from the dedicated /resolutions endpoint
        related_domains = resolutions if resolutions else []

        return {
            "source": self.name,
            "ok": True,
            "data": {
                "last_analysis_stats": last_analysis_stats,
                "reputation": reputation,
                "malicious_count": malicious,
                "suspicious_count": suspicious,
                "harmless_count": harmless,
                "undetected_count": undetected,
                "as_owner": as_owner,
                "tags": tags,
                "related_domains": related_domains,
            },
        }

    def _empty_response(self) -> Dict[str, Any]:
        """Return empty response for IPs not found in VT."""
        return {
            "source": self.name,
            "ok": True,
            "data": {
                "last_analysis_stats": {},
                "reputation": 0.0,
                "malicious_count": 0,
                "suspicious_count": 0,
                "harmless_count": 0,
                "undetected_count": 0,
                "as_owner": "",
                "tags": [],
                "related_domains": [],
            },
        }

    def _error_response(self, error: str) -> Dict[str, Any]:
        """Return error response."""
        return {"source": self.name, "ok": False, "data": None, "error": error}
