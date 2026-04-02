"""
IP profile normalizer for standardizing collected data.

v2.0: Source-specific extraction (_extract_rdap_data, _extract_reverse_dns_data)
has been moved into the RDAP and ReverseDNS plugins. This module is retained
for backward compatibility and timestamp handling. In v2.0 flow, the
QueryEngine directly builds the IPProfile from PluginResults.
"""

import logging
from datetime import datetime
from typing import Dict, Any
from models import IPProfile

logger = logging.getLogger(__name__)


class IPNormalizer:
    """Normalizes collected threat intelligence data into IPProfile.

    v2.0: Most normalization logic has been absorbed by plugins.
    This class is retained for v1.0 backward-compat if ever needed.
    """

    def normalize(
        self, ip: str, collected_data: Dict[str, Dict[str, Any]]
    ) -> IPProfile:
        """
        Normalize collected data from all sources into a unified IPProfile.

        Args:
            ip: The IP address being analyzed
            collected_data: Dict mapping source names to their results

        Returns:
            Normalized IPProfile
        """
        profile = IPProfile(ip=ip)

        # Store all raw source data
        profile.sources = collected_data

        # Set timestamps
        profile.timestamps = {
            "normalized_at": datetime.now(),
            "collected_at": datetime.now(),
        }

        return profile
