"""
VirusTotal plugin — multi-engine malware detection and passive DNS.

Absorbs:
  - collectors/virustotal.py (collection + passive DNS resolutions)
  - scoring_rules.yaml sources.virustotal (scoring)
"""

import logging
from typing import Any

import httpx
from models import EvidenceItem
from plugins.base import TIPlugin, PluginMetadata, PluginResult

logger = logging.getLogger(__name__)


class VirusTotalPlugin(TIPlugin):
    """Self-contained VirusTotal threat intelligence plugin."""

    BASE_URL = "https://www.virustotal.com/api/v3"

    @property
    def metadata(self) -> PluginMetadata:
        return PluginMetadata(
            name="virustotal",
            display_name="VirusTotal",
            version="1.0.0",
            supported_types=["ip"],
            requires_api_key=True,
            api_key_env_var="VT_API_KEY",
            rate_limit=4.0,  # 4 req/min on free tier
            priority=10,
            tags=["reputation", "malware", "passive_dns"],
            description="Multi-engine malware detection and passive DNS resolutions",
        )

    async def query(self, observable: str, obs_type: str) -> PluginResult:
        api_key = self._get_api_key()
        if not api_key:
            logger.warning("VirusTotal API key not configured, skipping")
            return self._error_result("API key not configured")

        try:
            headers = {"x-apikey": api_key, "accept": "application/json"}
            timeout = httpx.Timeout(15.0)

            logger.info(f"VirusTotal querying {observable}")
            async with httpx.AsyncClient(timeout=timeout) as client:
                # 1) Base IP report
                url = f"{self.BASE_URL}/ip_addresses/{observable}"
                response = await client.get(url, headers=headers)
                response.raise_for_status()
                data = response.json()

                # 2) Passive DNS resolutions
                max_resolutions = self.plugin_config.get("max_resolutions", 20)
                resolutions = await self._fetch_resolutions(
                    client, headers, observable, max_resolutions
                )

                return self._build_result(data, resolutions)

        except httpx.HTTPStatusError as e:
            if e.response.status_code == 404:
                logger.info(f"VirusTotal: IP {observable} not found (404)")
                return self._empty_result()
            logger.error(
                f"VirusTotal HTTP error for {observable}: HTTP {e.response.status_code}"
            )
            return self._error_result(f"HTTP {e.response.status_code}")
        except Exception as e:
            logger.error(f"VirusTotal error for {observable}: {e}")
            return self._error_result(str(e))

    async def _fetch_resolutions(
        self,
        client: httpx.AsyncClient,
        headers: dict[str, str],
        ip: str,
        limit: int,
    ) -> list[str]:
        """Fetch passive DNS resolutions. Best-effort — never fails the parent query."""
        try:
            url = f"{self.BASE_URL}/ip_addresses/{ip}/resolutions"
            resp = await client.get(url, headers=headers, params={"limit": limit})
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

    def _build_result(
        self, data: dict[str, Any], resolutions: list[str]
    ) -> PluginResult:
        """Parse response, normalize, score."""
        attributes = data.get("data", {}).get("attributes", {})

        last_analysis_stats = attributes.get("last_analysis_stats", {})
        malicious = last_analysis_stats.get("malicious", 0)
        suspicious = last_analysis_stats.get("suspicious", 0)
        harmless = last_analysis_stats.get("harmless", 0)
        undetected = last_analysis_stats.get("undetected", 0)

        total = malicious + suspicious + harmless + undetected
        reputation = (malicious + suspicious) / total if total > 0 else 0

        standardized = {
            "last_analysis_stats": last_analysis_stats,
            "reputation": reputation,
            "malicious_count": malicious,
            "suspicious_count": suspicious,
            "harmless_count": harmless,
            "undetected_count": undetected,
            "as_owner": attributes.get("as_owner", ""),
            "tags": attributes.get("tags", []),
            "related_domains": resolutions,
        }

        evidence = self._score(standardized)

        logger.info(
            f"VirusTotal result: malicious={malicious}, suspicious={suspicious}, "
            f"resolutions={len(resolutions)}"
        )

        return PluginResult(
            source=self.metadata.name,
            ok=True,
            raw_data=standardized,
            normalized_data=None,
            evidence=evidence,
            error=None,
        )

    def _empty_result(self) -> PluginResult:
        """Return result for IPs not found in VT."""
        return PluginResult(
            source=self.metadata.name,
            ok=True,
            raw_data={
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
            normalized_data=None,
            evidence=[],
            error=None,
        )

    def _score(self, data: dict[str, Any]) -> list[EvidenceItem]:
        """Apply scoring rules (from scoring_rules.yaml sources.virustotal)."""
        evidence: list[EvidenceItem] = []
        malicious = data.get("malicious_count", 0)
        suspicious = data.get("suspicious_count", 0)

        if malicious > 0:
            score_delta = min(30, malicious * 5)
            evidence.append(
                EvidenceItem(
                    source="virustotal",
                    category="reputation",
                    severity="high",
                    title="VirusTotal malicious detections",
                    detail=f"VirusTotal detected {malicious} malicious engines",
                    score_delta=score_delta,
                    confidence=0.5,
                )
            )

        if suspicious > 0:
            score_delta = min(15, suspicious * 3)
            evidence.append(
                EvidenceItem(
                    source="virustotal",
                    category="reputation",
                    severity="medium",
                    title="VirusTotal suspicious detections",
                    detail=f"VirusTotal detected {suspicious} suspicious engines",
                    score_delta=score_delta,
                    confidence=0.5,
                )
            )

        return evidence
