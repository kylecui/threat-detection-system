"""
Shodan IP collector.
"""

import logging
from typing import Dict, Any, Optional, List
import httpx
from app.config import settings
from .base import BaseCollector

logger = logging.getLogger(__name__)


class ShodanCollector(BaseCollector):
    """Collector for Shodan IP intelligence."""

    name = "shodan"

    def __init__(self):
        super().__init__("shodan")
        self.api_key = settings.shodan_api_key
        self.base_url = "https://api.shodan.io"

    async def query(self, observable: str) -> Dict[str, Any]:
        """Query Shodan for IP information."""
        if not self.api_key:
            logger.warning("Shodan API key not configured, skipping")
            return self._error_response("API key not configured")

        try:
            url = f"{self.base_url}/shodan/host/{observable}"
            params = {"key": self.api_key}

            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.get(url, params=params)
                response.raise_for_status()

                data = response.json()
                return self._parse_response(data)

        except httpx.HTTPStatusError as e:
            if e.response.status_code == 404:
                # IP not found in Shodan
                return self._empty_response()
            elif e.response.status_code == 401:
                logger.error("Invalid Shodan API key")
                return self._error_response("Invalid API key")
            logger.error(f"Shodan HTTP error for {observable}: {e}")
            return self._error_response(f"HTTP {e.response.status_code}")
        except Exception as e:
            logger.error(f"Shodan error for {observable}: {e}")
            return self._error_response(str(e))

    def _parse_response(self, data: Dict[str, Any]) -> Dict[str, Any]:
        """Parse Shodan API response."""
        # Extract basic info
        ip = data.get("ip_str", "")
        ports = data.get("ports", [])
        hostnames = data.get("hostnames", [])
        domains = data.get("domains", [])
        country = data.get("country_name", "")
        city = data.get("city", "")
        org = data.get("org", "")
        isp = data.get("isp", "")

        # Extract service banners
        services = []
        for service_data in data.get("data", []):
            port = service_data.get("port")
            transport = service_data.get("transport", "tcp")
            product = service_data.get("product", "")
            version = service_data.get("version", "")
            banner = service_data.get("data", "").strip()
            ssl = service_data.get("ssl", {})

            service_info = {
                "port": port,
                "transport": transport,
                "product": product,
                "version": version,
                "banner": banner[:500] if banner else "",  # Limit banner size
                "ssl_cert": ssl.get("cert", {}).get("subject", {}).get("CN", "")
                if ssl
                else "",
            }
            services.append(service_info)

        # Extract vulnerabilities if available
        vulns = data.get("vulns", [])

        return {
            "source": self.name,
            "ok": True,
            "data": {
                "ip": ip,
                "ports": ports,
                "hostnames": hostnames,
                "domains": domains,
                "country": country,
                "city": city,
                "organization": org,
                "isp": isp,
                "services": services,
                "vulnerabilities": list(vulns.keys()) if vulns else [],
                "vulns_count": len(vulns),
            },
        }

    def _empty_response(self) -> Dict[str, Any]:
        """Return empty response for IPs not found in Shodan."""
        return {
            "source": self.name,
            "ok": True,
            "data": {
                "ip": "",
                "ports": [],
                "hostnames": [],
                "domains": [],
                "country": "",
                "city": "",
                "organization": "",
                "isp": "",
                "services": [],
                "vulnerabilities": [],
                "vulns_count": 0,
            },
        }

    def _error_response(self, error: str) -> Dict[str, Any]:
        """Return error response."""
        return {"source": self.name, "ok": False, "data": None, "error": error}
