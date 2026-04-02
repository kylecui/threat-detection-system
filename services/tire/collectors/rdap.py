"""
RDAP collector for IP registration data.
"""

import logging
import json
from typing import Dict, Any, Optional
from .base import BaseCollector

logger = logging.getLogger(__name__)


class RDAPCollector(BaseCollector):
    """Collector for RDAP (Registration Data Access Protocol) data."""

    def __init__(self):
        super().__init__("rdap")
        # No API key needed for RDAP

    async def query(self, observable: str) -> Dict[str, Any]:
        """
        Query RDAP for IP registration information.

        Args:
            observable: IP address to query

        Returns:
            Standardized response dict
        """
        # Use ipwhois library or direct RDAP queries
        # For now, implement basic RDAP query
        url = f"https://rdap.arin.net/registry/ip/{observable}"

        result = await self._make_request(url, follow_redirects=True)

        if result["ok"]:
            data = result["data"]
            standardized_data = self._parse_rdap_response(data)
            result["data"] = standardized_data

        return {
            "source": self.name,
            "ok": result["ok"],
            "data": result["data"],
            "error": result["error"],
        }

    def _parse_rdap_response(self, data: Dict[str, Any]) -> Dict[str, Any]:
        """Parse RDAP JSON response into standardized format."""
        try:
            # Extract key information
            standardized = {
                "handle": data.get("handle"),
                "start_address": data.get("startAddress"),
                "end_address": data.get("endAddress"),
                "ip_version": data.get("ipVersion"),
                "name": data.get("name"),
                "type": data.get("type"),
                "country": data.get("country"),
                "parent_handle": data.get("parentHandle"),
            }

            # Extract ASN if available
            remarks = data.get("remarks", [])
            for remark in remarks:
                if remark.get("title") == "Registration Comments":
                    description = remark.get("description", [])
                    for desc in description:
                        if "AS" in desc and "Autonomous System" in desc:
                            # Extract ASN from description
                            import re

                            asn_match = re.search(r"AS(\d+)", desc)
                            if asn_match:
                                standardized["asn"] = f"AS{asn_match.group(1)}"
                                break

            # Extract entities
            entities = data.get("entities", [])
            if entities:
                entity_info = []
                for entity in entities:
                    entity_data = {
                        "handle": entity.get("handle"),
                        "roles": entity.get("roles", []),
                        "vcard": entity.get("vcardArray"),
                    }
                    entity_info.append(entity_data)
                standardized["entities"] = entity_info

            # Extract network information
            if "cidr0_cidrs" in data:
                cidrs = data["cidr0_cidrs"]
                if cidrs:
                    cidr = cidrs[0]
                    standardized["network"] = (
                        f"{cidr.get('v4prefix')}/{cidr.get('length')}"
                    )
            elif "cidr0_cidrs" in data.get("networks", [{}])[0]:
                networks = data.get("networks", [])
                if networks:
                    network = networks[0]
                    cidrs = network.get("cidr0_cidrs", [])
                    if cidrs:
                        cidr = cidrs[0]
                        standardized["network"] = (
                            f"{cidr.get('v4prefix')}/{cidr.get('length')}"
                        )

            return standardized

        except Exception as e:
            logger.error(f"Error parsing RDAP response: {e}")
            return {"error": "Failed to parse RDAP response"}
