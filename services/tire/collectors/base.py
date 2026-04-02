"""
Base collector interface for threat intelligence sources.
"""

import asyncio
import logging
from abc import ABC, abstractmethod
from typing import Dict, Any, Optional
from httpx import AsyncClient, Timeout
from app.config import settings

logger = logging.getLogger(__name__)


class BaseCollector(ABC):
    """Abstract base class for all threat intelligence collectors."""

    def __init__(self, name: str):
        self.name = name
        self.timeout = Timeout(settings.http_timeout_seconds)
        self.max_retries = settings.max_retries

    @abstractmethod
    async def query(self, observable: str) -> Dict[str, Any]:
        """
        Query the threat intelligence source.

        Args:
            observable: The observable to query (IP, domain, etc.)

        Returns:
            Dict containing:
            {
                "source": str,
                "ok": bool,
                "data": dict or None,
                "error": str or None
            }
        """
        pass

    async def _make_request(
        self,
        url: str,
        headers: Optional[Dict[str, str]] = None,
        params: Optional[Dict[str, Any]] = None,
        follow_redirects: bool = False,
    ) -> Dict[str, Any]:
        """
        Make HTTP request with error handling and retries.

        Returns:
            {
                "ok": bool,
                "data": dict or None,
                "error": str or None
            }
        """
        for attempt in range(self.max_retries + 1):
            try:
                async with AsyncClient(timeout=self.timeout) as client:
                    response = await client.get(
                        url,
                        headers=headers,
                        params=params,
                        follow_redirects=follow_redirects,
                    )
                    response.raise_for_status()

                    # Try to parse JSON response
                    try:
                        data = response.json()
                    except Exception:
                        data = {"raw_response": response.text}

                    return {"ok": True, "data": data, "error": None}

            except Exception as e:
                error_msg = f"{self.name} request failed (attempt {attempt + 1}/{self.max_retries + 1}): {str(e)}"
                logger.warning(error_msg)

                if attempt == self.max_retries:
                    return {"ok": False, "data": None, "error": error_msg}

                # Wait before retry
                await asyncio.sleep(1 * (attempt + 1))

        # This should not be reached
        return {"ok": False, "data": None, "error": "Unknown error"}
