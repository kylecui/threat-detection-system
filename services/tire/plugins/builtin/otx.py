"""
AlienVault OTX plugin — threat intelligence from OTX.

Absorbs:
  - collectors/otx.py (collection)
  - scoring_rules.yaml sources.otx (scoring)
"""

import logging
from typing import Any

from models import EvidenceItem
from plugins.base import TIPlugin, PluginMetadata, PluginResult

logger = logging.getLogger(__name__)


class OTXPlugin(TIPlugin):
    """Self-contained AlienVault OTX threat intelligence plugin."""

    @property
    def metadata(self) -> PluginMetadata:
        return PluginMetadata(
            name="otx",
            display_name="AlienVault OTX",
            version="1.0.0",
            supported_types=["ip"],
            requires_api_key=True,
            api_key_env_var="OTX_API_KEY",
            rate_limit=None,
            priority=20,
            tags=["reputation", "pulse"],
            description="OTX pulse-based threat intelligence",
        )

    async def query(self, observable: str, obs_type: str) -> PluginResult:
        api_key = self._get_api_key()
        if not api_key:
            logger.warning("OTX API key not configured, skipping query")
            return self._error_result("API key not configured")

        url = f"https://otx.alienvault.com/api/v1/indicators/IPv4/{observable}/general"
        headers = {"X-OTX-API-KEY": api_key, "Accept": "application/json"}

        result = await self._make_request(url, headers=headers)

        if not result["ok"]:
            return self._error_result(result["error"])

        raw = result["data"]

        # Normalize
        standardized = {
            "pulse_count": raw.get("pulse_info", {}).get("count", 0),
            "reputation": raw.get("reputation", 0),
            "sections": raw.get("sections", []),
            "validation": raw.get("validation", []),
            "base_indicator": raw.get("base_indicator", {}),
            "indicator": raw.get("indicator", observable),
        }

        passive_dns = raw.get("passive_dns", [])
        if passive_dns:
            standardized["passive_dns"] = passive_dns

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
        """Apply scoring rules (from scoring_rules.yaml sources.otx)."""
        evidence: list[EvidenceItem] = []
        pulse_count = data.get("pulse_count", 0)

        if pulse_count > 0:
            score_delta = min(20, pulse_count * 2)
            evidence.append(
                EvidenceItem(
                    source="otx",
                    category="reputation",
                    severity="medium",
                    title="OTX pulse activity",
                    detail=f"Found in {pulse_count} OTX pulses",
                    score_delta=score_delta,
                    confidence=0.5,
                )
            )

        return evidence
