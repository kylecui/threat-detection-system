"""
Plugin base classes for TIRE v2.0 plugin architecture.

Defines the TIPlugin ABC, PluginMetadata, and PluginResult dataclasses
that every threat intelligence plugin must implement.
"""

import asyncio
import logging
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any

from httpx import AsyncClient, Timeout
from models import EvidenceItem

logger = logging.getLogger(__name__)


@dataclass
class PluginMetadata:
    """Metadata describing a threat intelligence plugin."""

    name: str  # unique identifier, e.g. "virustotal"
    display_name: str  # human-readable, e.g. "VirusTotal"
    version: str  # semver, e.g. "1.0.0"
    supported_types: list[str]  # ["ip", "domain", "url", "hash"]
    requires_api_key: bool = False
    api_key_env_var: str | None = None
    rate_limit: float | None = None  # requests per second, or None
    priority: int = 50  # lower = runs first (default 50)
    tags: list[str] = field(default_factory=list)
    description: str = ""
    allow_env_fallback: bool = False


@dataclass
class PluginResult:
    """Result returned by a plugin's query() method."""

    source: str  # plugin name
    ok: bool
    raw_data: dict | None = None
    normalized_data: dict | None = None
    evidence: list[EvidenceItem] = field(default_factory=list)
    error: str | None = None


class TIPlugin(ABC):
    """Base class for all Threat Intelligence plugins.

    Each plugin is self-contained: it handles collection, normalization,
    AND scoring for its intelligence source. This eliminates cross-module
    coupling entirely.

    Subclasses MUST implement:
        - metadata (property) -> PluginMetadata
        - query(observable, obs_type) -> PluginResult
    """

    def __init__(self) -> None:
        self.plugin_config: dict[str, Any] = {}

    # ── Abstract interface ──────────────────────────────────────────

    @property
    @abstractmethod
    def metadata(self) -> PluginMetadata:
        """Return plugin metadata."""
        ...

    @abstractmethod
    async def query(self, observable: str, obs_type: str) -> PluginResult:
        """Execute the full plugin pipeline:

        1. Call external API / data source
        2. Normalize raw response
        3. Score and generate evidence

        Args:
            observable: The value to query (IP, domain, etc.)
            obs_type: The observable type ("ip", "domain", "url", "hash")

        Returns:
            PluginResult with raw data, normalized data, and evidence items
        """
        ...

    # ── Lifecycle hooks ─────────────────────────────────────────────

    def configure(self, config: dict[str, Any]) -> None:
        """Receive plugin-specific configuration from plugins.yaml.

        Called once at registration time. Override if needed.
        """
        self.plugin_config = config

    def on_register(self) -> None:
        """Lifecycle hook: called when plugin is registered."""
        pass

    def on_enable(self) -> None:
        """Lifecycle hook: called when plugin is enabled."""
        pass

    def on_disable(self) -> None:
        """Lifecycle hook: called when plugin is disabled."""
        pass

    # ── Shared utilities for plugins ────────────────────────────────

    def set_api_key_override(self, key: str | None) -> None:
        """Set a per-request API key override (e.g. from per-user storage).

        Called by QueryEngine before each plugin query to inject the
        resolved key from the configured-key chain.

        Pass None to clear any previous override.
        """
        self._override_api_key: str | None = key

    def _get_api_key(self) -> str | None:
        """Resolve API key with per-request override support only."""
        override = getattr(self, "_override_api_key", None)
        if override:
            return override
        return None

    async def _make_request(
        self,
        url: str,
        *,
        method: str = "GET",
        headers: dict[str, str] | None = None,
        params: dict[str, Any] | None = None,
        json_body: dict[str, Any] | None = None,
        timeout: float = 15.0,
        max_retries: int = 2,
        follow_redirects: bool = False,
    ) -> dict[str, Any]:
        """Make HTTP request with error handling and retries.

        This replaces the old BaseCollector._make_request, available
        to all plugins as a shared utility.

        Returns:
            {"ok": bool, "data": dict | None, "error": str | None}
        """
        for attempt in range(max_retries + 1):
            try:
                async with AsyncClient(timeout=Timeout(timeout)) as client:
                    if method.upper() == "GET":
                        response = await client.get(
                            url,
                            headers=headers,
                            params=params,
                            follow_redirects=follow_redirects,
                        )
                    elif method.upper() == "POST":
                        response = await client.post(
                            url,
                            headers=headers,
                            params=params,
                            json=json_body,
                            follow_redirects=follow_redirects,
                        )
                    else:
                        raise ValueError(f"Unsupported HTTP method: {method}")

                    response.raise_for_status()

                    try:
                        data = response.json()
                    except Exception:
                        data = {"raw_response": response.text}

                    return {"ok": True, "data": data, "error": None}

            except Exception as e:
                error_msg = (
                    f"{self.metadata.name} request failed "
                    f"(attempt {attempt + 1}/{max_retries + 1}): {e}"
                )
                logger.warning(error_msg)

                if attempt == max_retries:
                    return {"ok": False, "data": None, "error": error_msg}

                await asyncio.sleep(1 * (attempt + 1))

        return {"ok": False, "data": None, "error": "Unknown error"}

    def _error_result(self, error: str) -> PluginResult:
        """Convenience: return a failed PluginResult."""
        return PluginResult(
            source=self.metadata.name,
            ok=False,
            raw_data=None,
            normalized_data=None,
            evidence=[],
            error=error,
        )
