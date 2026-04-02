"""
Collectors package for threat intelligence sources.
"""

import asyncio
import logging
from typing import Dict, Any, List
from .base import BaseCollector
from .abuseipdb import AbuseIPDBCollector
from .otx import OTXCollector
from .greynoise import GreyNoiseCollector
from .rdap import RDAPCollector
from .reverse_dns import ReverseDNSCollector
from .virustotal import VirusTotalCollector
from .shodan import ShodanCollector
from .honeynet import HoneynetCollector
from .internal_flow import InternalFlowCollector

logger = logging.getLogger(__name__)


class CollectorAggregator:
    """Aggregates multiple collectors and runs them concurrently."""

    def __init__(self):
        self.collectors = self._initialize_collectors()

    def _initialize_collectors(self) -> List[BaseCollector]:
        """Initialize all available collectors."""
        collectors = [
            AbuseIPDBCollector(),
            OTXCollector(),
            GreyNoiseCollector(),
            RDAPCollector(),
            ReverseDNSCollector(),
            VirusTotalCollector(),
            ShodanCollector(),
            HoneynetCollector(),
            InternalFlowCollector(),
        ]
        return collectors

    async def collect_all(self, observable: str) -> Dict[str, Dict[str, Any]]:
        """
        Run all collectors concurrently and aggregate results.

        Args:
            observable: The observable to query

        Returns:
            Dict mapping source names to their results
        """
        # Create tasks for all collectors
        tasks = []
        for collector in self.collectors:
            task = asyncio.create_task(self._safe_collect(collector, observable))
            tasks.append(task)

        # Wait for all to complete
        results = await asyncio.gather(*tasks, return_exceptions=True)

        # Aggregate results
        aggregated = {}
        for i, result in enumerate(results):
            collector_name = self.collectors[i].name
            if isinstance(result, Exception):
                logger.error(
                    f"Collector {collector_name} failed with exception: {result}"
                )
                aggregated[collector_name] = {
                    "source": collector_name,
                    "ok": False,
                    "data": None,
                    "error": str(result),
                }
            else:
                ok = result.get("ok", False)
                error = result.get("error")
                logger.info(
                    f"Collector {collector_name}: ok={ok}"
                    + (f", error={error}" if error else "")
                )
                aggregated[collector_name] = result

        return aggregated

    async def _safe_collect(
        self, collector: BaseCollector, observable: str
    ) -> Dict[str, Any]:
        """Safely run a single collector with error handling."""
        try:
            return await collector.query(observable)
        except Exception as e:
            logger.error(f"Unexpected error in collector {collector.name}: {e}")
            return {
                "source": collector.name,
                "ok": False,
                "data": None,
                "error": str(e),
            }
