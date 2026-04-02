"""
Shodan plugin — internet-facing service and vulnerability data.

Absorbs:
  - collectors/shodan.py (collection)
  - No scoring rules (data-only for NoiseEngine)
"""

import logging
from typing import Any

import httpx
from models import EvidenceItem
from plugins.base import TIPlugin, PluginMetadata, PluginResult

logger = logging.getLogger(__name__)


class ShodanPlugin(TIPlugin):
    """Self-contained Shodan threat intelligence plugin."""

    BASE_URL = "https://api.shodan.io"

    @property
    def metadata(self) -> PluginMetadata:
        return PluginMetadata(
            name="shodan",
            display_name="Shodan",
            version="1.0.0",
            supported_types=["ip"],
            requires_api_key=True,
            api_key_env_var="SHODAN_API_KEY",
            rate_limit=1.0,
            priority=30,
            tags=["infrastructure", "ports", "services"],
            description="Internet-facing service banners and vulnerability data",
        )

    async def query(self, observable: str, obs_type: str) -> PluginResult:
        api_key = self._get_api_key()
        if not api_key:
            logger.warning("Shodan API key not configured, skipping")
            return self._error_result("API key not configured")

        try:
            url = f"{self.BASE_URL}/shodan/host/{observable}"
            params: dict[str, Any] = {"key": api_key}

            async with httpx.AsyncClient(timeout=httpx.Timeout(15.0)) as client:
                response = await client.get(url, params=params)
                response.raise_for_status()
                data = response.json()
                return self._build_result(data)

        except httpx.HTTPStatusError as e:
            if e.response.status_code == 404:
                return self._empty_result()
            elif e.response.status_code == 401:
                logger.error("Invalid Shodan API key")
                return self._error_result("Invalid API key")
            logger.error(f"Shodan HTTP error for {observable}: {e}")
            return self._error_result(f"HTTP {e.response.status_code}")
        except Exception as e:
            logger.error(f"Shodan error for {observable}: {e}")
            return self._error_result(str(e))

    def _build_result(self, data: dict[str, Any]) -> PluginResult:
        """Parse Shodan response and normalize."""
        ip = data.get("ip_str", "")
        ports = data.get("ports", [])
        hostnames = data.get("hostnames", [])
        domains = data.get("domains", [])
        country = data.get("country_name", "")
        city = data.get("city", "")
        org = data.get("org", "")
        isp = data.get("isp", "")

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
                "banner": banner[:500] if banner else "",
                "ssl_cert": ssl.get("cert", {}).get("subject", {}).get("CN", "")
                if ssl
                else "",
            }
            services.append(service_info)

        vulns = data.get("vulns", [])

        standardized = {
            "ip": ip,
            "ports": ports,
            "hostnames": hostnames,
            "domains": domains,
            "country": country,
            "city": city,
            "organization": org,
            "isp": isp,
            "services": services,
            "vulnerabilities": list(vulns.keys()) if isinstance(vulns, dict) else [],
            "vulns_count": len(vulns) if vulns else 0,
        }

        # Shodan has no scoring rules — data-only (consumed by NoiseEngine)
        return PluginResult(
            source=self.metadata.name,
            ok=True,
            raw_data=standardized,
            normalized_data=None,
            evidence=[],
            error=None,
        )

    def _empty_result(self) -> PluginResult:
        """Return result for IPs not found in Shodan."""
        return PluginResult(
            source=self.metadata.name,
            ok=True,
            raw_data={
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
            normalized_data=None,
            evidence=[],
            error=None,
        )
