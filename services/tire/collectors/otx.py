"""
AlienVault OTX collector for threat intelligence.
"""

import logging
from typing import Dict, Any
from .base import BaseCollector
from app.config import settings

logger = logging.getLogger(__name__)


class OTXCollector(BaseCollector):
    """Collector for AlienVault OTX threat intelligence."""

    def __init__(self):
        super().__init__("otx")
        self.api_key = settings.otx_api_key
        self.base_url = "https://otx.alienvault.com/api/v1"

    async def query(self, observable: str) -> Dict[str, Any]:
        """
        Query OTX for IP reputation.

        Args:
            observable: IP address to query

        Returns:
            Standardized response dict
        """
        if not self.api_key:
            logger.warning("OTX API key not configured, skipping query")
            return {
                "source": self.name,
                "ok": False,
                "data": None,
                "error": "API key not configured",
            }

        url = f"{self.base_url}/indicators/IPv4/{observable}/general"
        headers = {"X-OTX-API-KEY": self.api_key, "Accept": "application/json"}

        result = await self._make_request(url, headers=headers)

        if result["ok"]:
            data = result["data"]
            standardized_data = {
                "pulse_count": data.get("pulse_info", {}).get("count", 0),
                "reputation": data.get("reputation", 0),
                "sections": data.get("sections", []),
                "validation": data.get("validation", []),
                "base_indicator": data.get("base_indicator", {}),
                "indicator": data.get("indicator", observable),
            }

            # Extract passive DNS if available
            passive_dns = data.get("passive_dns", [])
            if passive_dns:
                standardized_data["passive_dns"] = passive_dns

            result["data"] = standardized_data

        return {
            "source": self.name,
            "ok": result["ok"],
            "data": result["data"],
            "error": result["error"],
        }
