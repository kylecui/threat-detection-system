"""
RDAP plugin — IP registration data via RDAP.

Absorbs:
  - collectors/rdap.py (collection)
  - normalizers/ip_normalizer.py _extract_rdap_data (profile normalization)
  - No scoring rules
"""

import logging
import re
from typing import Any

from models import EvidenceItem
from plugins.base import TIPlugin, PluginMetadata, PluginResult

logger = logging.getLogger(__name__)


class RDAPPlugin(TIPlugin):
    """Self-contained RDAP registration data plugin."""

    @property
    def metadata(self) -> PluginMetadata:
        return PluginMetadata(
            name="rdap",
            display_name="RDAP",
            version="1.0.0",
            supported_types=["ip"],
            requires_api_key=False,
            api_key_env_var=None,
            rate_limit=None,
            priority=5,
            tags=["registration", "whois", "network"],
            description="IP registration data via RDAP (organization, ASN, network)",
        )

    async def query(self, observable: str, obs_type: str) -> PluginResult:
        url = f"https://rdap.arin.net/registry/ip/{observable}"

        result = await self._make_request(url, follow_redirects=True)

        if not result["ok"]:
            return self._error_result(result["error"])

        raw = result["data"]
        standardized = self._parse_rdap(raw)

        # Profile-level normalization — fields to apply to IPProfile
        normalized = self._extract_profile_data(standardized)

        return PluginResult(
            source=self.metadata.name,
            ok=True,
            raw_data=standardized,
            normalized_data=normalized,
            evidence=[],  # RDAP has no scoring rules
            error=None,
        )

    def _parse_rdap(self, data: dict[str, Any]) -> dict[str, Any]:
        """Parse RDAP JSON response into standardized format."""
        try:
            standardized: dict[str, Any] = {
                "handle": data.get("handle"),
                "start_address": data.get("startAddress"),
                "end_address": data.get("endAddress"),
                "ip_version": data.get("ipVersion"),
                "name": data.get("name"),
                "type": data.get("type"),
                "country": data.get("country"),
                "parent_handle": data.get("parentHandle"),
            }

            # Extract ASN from remarks
            remarks = data.get("remarks", [])
            for remark in remarks:
                if remark.get("title") == "Registration Comments":
                    description = remark.get("description", [])
                    for desc in description:
                        if "AS" in desc and "Autonomous System" in desc:
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

            # Extract network CIDR
            if "cidr0_cidrs" in data:
                cidrs = data["cidr0_cidrs"]
                if cidrs:
                    cidr = cidrs[0]
                    prefix = cidr.get("v4prefix")
                    length = cidr.get("length")
                    if prefix and length:
                        standardized["network"] = f"{prefix}/{length}"

            return standardized

        except Exception as e:
            logger.error(f"Error parsing RDAP response: {e}")
            return {"error": "Failed to parse RDAP response"}

    def _extract_profile_data(self, rdap_data: dict[str, Any]) -> dict[str, Any]:
        """Extract profile-level fields for IPProfile normalization.

        These will be applied by QueryEngine._apply_profile_data():
            profile.organization = normalized_data["organization"]
            profile.country      = normalized_data["country"]
            profile.asn          = normalized_data["asn"]
            profile.network      = normalized_data["network"]
        """
        normalized: dict[str, Any] = {}

        if rdap_data.get("name"):
            normalized["organization"] = rdap_data["name"]

        if rdap_data.get("country"):
            normalized["country"] = rdap_data["country"]

        if rdap_data.get("asn"):
            normalized["asn"] = rdap_data["asn"]

        if rdap_data.get("network"):
            normalized["network"] = rdap_data["network"]

        return normalized if normalized else {}
