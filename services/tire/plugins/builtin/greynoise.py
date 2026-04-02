"""
GreyNoise plugin — internet noise classification.

Absorbs:
  - collectors/greynoise.py (collection)
  - scoring_rules.yaml sources.greynoise (scoring)
"""

import logging
from typing import Any

from models import EvidenceItem
from plugins.base import TIPlugin, PluginMetadata, PluginResult

logger = logging.getLogger(__name__)


class GreyNoisePlugin(TIPlugin):
    """Self-contained GreyNoise threat intelligence plugin."""

    @property
    def metadata(self) -> PluginMetadata:
        return PluginMetadata(
            name="greynoise",
            display_name="GreyNoise",
            version="1.0.0",
            supported_types=["ip"],
            requires_api_key=True,
            api_key_env_var="GREYNOISE_API_KEY",
            rate_limit=None,
            priority=20,
            tags=["noise", "classification"],
            description="Internet noise and scanning classification",
            allow_env_fallback=False,
        )

    async def query(self, observable: str, obs_type: str) -> PluginResult:
        api_key = self._get_api_key()

        url = f"https://api.greynoise.io/v3/community/{observable}"
        headers = {"Accept": "application/json"}
        if api_key:
            headers["key"] = api_key
        else:
            logger.info("GreyNoise API key not configured, using community mode")

        result = await self._make_request(url, headers=headers)

        if not result["ok"]:
            return self._error_result(result["error"])

        raw = result["data"]

        # Normalize
        standardized = {
            "ip": raw.get("ip"),
            "noise": raw.get("noise", False),
            "riot": raw.get("riot", False),
            "classification": raw.get("classification"),
            "name": raw.get("name"),
            "link": raw.get("link"),
            "last_seen": raw.get("last_seen"),
            "message": raw.get("message"),
        }

        # Score — absorbed from scoring_rules.yaml
        evidence = self._score(standardized)

        return PluginResult(
            source=self.metadata.name,
            ok=True,
            raw_data=standardized,
            normalized_data=None,
            evidence=evidence,
            error=None,
        )

    def _score(self, data: dict[str, Any]) -> list[EvidenceItem]:
        """Apply scoring rules (from scoring_rules.yaml sources.greynoise)."""
        evidence: list[EvidenceItem] = []
        classification = data.get("classification", "")
        noise = data.get("noise", False)
        riot = data.get("riot", False)

        if classification == "malicious":
            evidence.append(
                EvidenceItem(
                    source="greynoise",
                    category="reputation",
                    severity="high",
                    title="GreyNoise malicious classification",
                    detail="IP classified as malicious by GreyNoise",
                    score_delta=20,
                    confidence=0.9,
                )
            )
        elif noise or riot:
            activity_type = "noise" if noise else "RIOT"
            evidence.append(
                EvidenceItem(
                    source="greynoise",
                    category="reputation",
                    severity="low",
                    title="GreyNoise benign activity",
                    detail=f"IP classified as {activity_type} - benign background activity",
                    score_delta=-15,
                    confidence=0.9,
                )
            )

        return evidence
