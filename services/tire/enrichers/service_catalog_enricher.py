"""
Service catalog enricher for semantic infrastructure identification.
"""

import logging
import re
from typing import List, Dict, Any, Optional
import yaml
from models import IPProfile

logger = logging.getLogger(__name__)


class ServiceCatalogEnricher:
    """Enriches IP profiles with semantic tags from service catalog."""

    def __init__(self, catalog_path: str = "rules/service_catalog.yaml"):
        self.catalog_path = catalog_path
        self.catalog = self._load_catalog()

    def _load_catalog(self) -> Dict[str, Any]:
        """Load the service catalog from YAML file."""
        try:
            with open(self.catalog_path, "r", encoding="utf-8") as f:
                return yaml.safe_load(f)
        except Exception as e:
            logger.error(f"Failed to load service catalog: {e}")
            return {}

    def enrich(self, profile: IPProfile) -> IPProfile:
        """
        Enrich IP profile with semantic tags based on service catalog.

        Args:
            profile: IPProfile to enrich

        Returns:
            Enriched IPProfile with additional tags
        """
        if not self.catalog:
            logger.warning("Service catalog not loaded, skipping enrichment")
            return profile

        # Initialize tags if not present
        if profile.tags is None:
            profile.tags = []

        # Check each provider in the catalog
        providers = self.catalog.get("providers", {})
        for provider_name, provider_config in providers.items():
            if not provider_config.get("enabled", False):
                continue

            tags = self._match_provider(profile, provider_config)
            if tags:
                profile.tags.extend(tags)
                logger.debug(
                    f"Added tags {tags} for provider {provider_name} to IP {profile.ip}"
                )

        # Remove duplicates while preserving order
        profile.tags = list(dict.fromkeys(profile.tags))

        return profile

    def _match_provider(
        self, profile: IPProfile, provider_config: Dict[str, Any]
    ) -> List[str]:
        """
        Check if profile matches a provider's criteria.

        Returns:
            List of tags to add if matched
        """
        match_config = provider_config.get("match", {})
        tags = provider_config.get("tags", [])
        confidence = provider_config.get("confidence", 0.0)

        # Skip if confidence too low
        min_confidence = self.catalog.get("thresholds", {}).get(
            "min_confidence_to_apply", 0.7
        )
        if confidence < min_confidence:
            return []

        # Calculate match score
        match_score = self._calculate_match_score(profile, match_config)

        # Apply if score meets threshold
        min_score = self.catalog.get("thresholds", {}).get(
            "min_match_score_to_apply", 1.0
        )
        if match_score >= min_score:
            return tags

        return []

    def _calculate_match_score(
        self, profile: IPProfile, match_config: Dict[str, Any]
    ) -> float:
        """Calculate how well the profile matches the provider criteria."""
        score = 0.0
        weights = self.catalog.get("matching_logic", {})

        # Organization match
        if self._matches_organization(
            profile.organization, match_config.get("organizations", [])
        ):
            score += weights.get("organization_match_weight", 1.0)

        # ASN match
        if self._matches_asn(profile.asn, match_config.get("asns", [])):
            score += weights.get("asn_match_weight", 1.0)

        # Hostname keyword matches
        hostname_matches = self._count_keyword_matches(
            profile.hostnames or [], match_config.get("hostname_keywords", [])
        )
        if hostname_matches > 0:
            score += weights.get("hostname_keyword_match_weight", 0.7) * min(
                hostname_matches, 2
            )  # Cap at 2

        # RDNS keyword matches
        rdns_matches = self._count_keyword_matches(
            profile.rdns or [], match_config.get("rdns_keywords", [])
        )
        if rdns_matches > 0:
            score += weights.get("rdns_keyword_match_weight", 0.7) * min(
                rdns_matches, 2
            )  # Cap at 2

        # Multiple match bonus
        if score > 1.0:
            score += weights.get("multiple_match_bonus", 0.2)

        return score

    def _matches_organization(self, org: Optional[str], org_list: List[str]) -> bool:
        """Check if organization matches any in the list."""
        if not org or not org_list:
            return False

        org_lower = org.lower()
        return any(org_pattern.lower() in org_lower for org_pattern in org_list)

    def _matches_asn(self, asn: Optional[str], asn_list: List[str]) -> bool:
        """Check if ASN matches any in the list."""
        if not asn or not asn_list:
            return False

        return asn.upper() in [a.upper() for a in asn_list]

    def _count_keyword_matches(self, strings: List[str], keywords: List[str]) -> int:
        """Count how many strings contain any of the keywords."""
        if not strings or not keywords:
            return 0

        count = 0
        for string in strings:
            if string:
                string_lower = string.lower()
                if any(keyword.lower() in string_lower for keyword in keywords):
                    count += 1

        return count
