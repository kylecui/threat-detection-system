"""
Semantic enricher that coordinates multiple enrichment processes.
"""

import logging
from models import IPProfile
from .service_catalog_enricher import ServiceCatalogEnricher

logger = logging.getLogger(__name__)


class SemanticEnricher:
    """Coordinates semantic enrichment of IP profiles."""

    def __init__(self):
        self.service_catalog = ServiceCatalogEnricher()

    def enrich(self, profile: IPProfile) -> IPProfile:
        """
        Apply all semantic enrichments to the IP profile.

        Args:
            profile: IPProfile to enrich

        Returns:
            Enriched IPProfile
        """
        # Apply service catalog enrichment
        profile = self.service_catalog.enrich(profile)

        # Additional semantic enrichments can be added here
        # For now, service catalog is the main enrichment

        logger.debug(f"Enriched IP {profile.ip} with tags: {profile.tags}")
        return profile
