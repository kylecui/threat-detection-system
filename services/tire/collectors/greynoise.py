"""
GreyNoise collector for threat intelligence.
"""

import logging
from typing import Dict, Any
from .base import BaseCollector
from app.config import settings

logger = logging.getLogger(__name__)


class GreyNoiseCollector(BaseCollector):
    """Collector for GreyNoise threat intelligence."""

    def __init__(self):
        super().__init__("greynoise")
        self.api_key = settings.greynoise_api_key
        self.base_url = "https://api.greynoise.io/v3"

    async def query(self, observable: str) -> Dict[str, Any]:
        """
        Query GreyNoise community API for IP classification.

        Args:
            observable: IP address to query

        Returns:
            Standardized response dict
        """
        if not self.api_key:
            logger.warning("GreyNoise API key not configured, skipping query")
            return {
                "source": self.name,
                "ok": False,
                "data": None,
                "error": "API key not configured",
            }

        url = f"{self.base_url}/community/{observable}"
        headers = {"Accept": "application/json", "key": self.api_key}

        result = await self._make_request(url, headers=headers)

        if result["ok"]:
            data = result["data"]
            standardized_data = {
                "ip": data.get("ip"),
                "noise": data.get("noise", False),
                "riot": data.get("riot", False),
                "classification": data.get("classification"),
                "name": data.get("name"),
                "link": data.get("link"),
                "last_seen": data.get("last_seen"),
                "message": data.get("message"),
            }
            result["data"] = standardized_data

        return {
            "source": self.name,
            "ok": result["ok"],
            "data": result["data"],
            "error": result["error"],
        }
