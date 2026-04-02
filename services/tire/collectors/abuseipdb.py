"""
AbuseIPDB collector for threat intelligence.
"""

import logging
from typing import Dict, Any
from .base import BaseCollector
from app.config import settings

logger = logging.getLogger(__name__)


class AbuseIPDBCollector(BaseCollector):
    """Collector for AbuseIPDB threat intelligence."""

    def __init__(self):
        super().__init__("abuseipdb")
        self.api_key = settings.abuseipdb_api_key
        self.base_url = "https://api.abuseipdb.com/api/v2"

    async def query(self, observable: str) -> Dict[str, Any]:
        """
        Query AbuseIPDB for IP reputation.

        Args:
            observable: IP address to query

        Returns:
            Standardized response dict
        """
        if not self.api_key:
            logger.warning("AbuseIPDB API key not configured, skipping query")
            return {
                "source": self.name,
                "ok": False,
                "data": None,
                "error": "API key not configured",
            }

        url = f"{self.base_url}/check"
        headers = {"Accept": "application/json", "Key": self.api_key}
        params = {"ipAddress": observable, "maxAgeInDays": 90, "verbose": "true"}

        result = await self._make_request(url, headers=headers, params=params)

        if result["ok"]:
            # Extract relevant fields
            data = result["data"].get("data", {})
            standardized_data = {
                "abuse_confidence_score": data.get("abuseConfidenceScore", 0),
                "total_reports": data.get("totalReports", 0),
                "country_code": data.get("countryCode"),
                "usage_type": data.get("usageType"),
                "isp": data.get("isp"),
                "domain": data.get("domain"),
                "is_whitelisted": data.get("isWhitelisted", False),
                "last_reported_at": data.get("lastReportedAt"),
                "num_distinct_users": data.get("numDistinctUsers", 0),
            }
            result["data"] = standardized_data

        return {
            "source": self.name,
            "ok": result["ok"],
            "data": result["data"],
            "error": result["error"],
        }
