"""Dynamic platform LLM configuration client.

The platform gateway is the source of truth. Successful results are cached
briefly, and the last valid result may be used during a bounded gateway outage.
No API key is ever written to logs.
"""

from __future__ import annotations

import asyncio
import logging
import time
from dataclasses import dataclass
from typing import Any, Optional

import httpx
from fastapi import Request

from app.config import settings

logger = logging.getLogger(__name__)


@dataclass
class _CacheEntry:
    value: dict[str, Any]
    fetched_at: float


class RuntimeLlmConfigClient:
    """Resolve per-request LLM settings from the protected gateway endpoint."""

    def __init__(self) -> None:
        self._cache: dict[tuple[str, str, str], _CacheEntry] = {}
        self._lock = asyncio.Lock()

    async def resolve(
        self, request: Request, *, local_user_id: int
    ) -> Optional[dict[str, Any]]:
        if not settings.llm_runtime_config_url and not settings.llm_runtime_service_token:
            # Compatibility mode for deployments that have not enabled the
            # platform runtime-config bridge.  ``None`` is intentionally
            # reserved for this state so the API layer may use TIRE's legacy
            # admin database/environment resolver.
            return None
        if not settings.llm_runtime_config_url or not settings.llm_runtime_service_token:
            logger.error(
                "Runtime LLM config bridge is partially configured; disabling LLM "
                "for this request"
            )
            return self._fail_closed(local_user_id)

        context = self._context_from_request(request)
        cache_key = (
            context.get("X-Context-User-Id", ""),
            context.get("X-Context-Customer-Id", ""),
            context.get("X-Context-Tenant-Id", ""),
        )
        now = time.monotonic()
        entry = self._cache.get(cache_key)
        if entry and now - entry.fetched_at < settings.llm_runtime_cache_ttl_seconds:
            return self._for_local_user(entry.value, local_user_id)

        async with self._lock:
            now = time.monotonic()
            entry = self._cache.get(cache_key)
            if entry and now - entry.fetched_at < settings.llm_runtime_cache_ttl_seconds:
                return self._for_local_user(entry.value, local_user_id)
            try:
                value = await self._fetch(context)
                self._cache[cache_key] = _CacheEntry(value=value, fetched_at=now)
                return self._for_local_user(value, local_user_id)
            except Exception as exc:
                stale = self._cache.get(cache_key)
                if (
                    stale
                    and now - stale.fetched_at
                    <= settings.llm_runtime_stale_ttl_seconds
                ):
                    logger.warning(
                        "Runtime LLM config refresh failed; using last valid config "
                        "(source=%s, error=%s)",
                        stale.value.get("source", "unknown"),
                        type(exc).__name__,
                    )
                    return self._for_local_user(stale.value, local_user_id)
                logger.warning(
                    "Runtime LLM config unavailable; disabling LLM for this request "
                    "(error=%s)",
                    type(exc).__name__,
                )
                # The runtime bridge is configured, so a failed first fetch is
                # not equivalent to a legacy deployment.  Falling back to a
                # local or environment key here could bypass a platform
                # ``admin_locked`` decision that TIRE cannot currently read.
                return self._fail_closed(local_user_id)

    async def _fetch(self, context: dict[str, str]) -> dict[str, Any]:
        headers = {
            "X-Internal-Service-Token": settings.llm_runtime_service_token,
            **context,
        }
        timeout = httpx.Timeout(connect=3.0, read=8.0, write=3.0, pool=3.0)
        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.get(settings.llm_runtime_config_url, headers=headers)
            response.raise_for_status()
            value = response.json()
        if not isinstance(value, dict) or "configured" not in value:
            raise ValueError("Runtime LLM config response is invalid")
        if value.get("configured"):
            for field in ("api_key", "model", "base_url", "fingerprint"):
                if not value.get(field):
                    raise ValueError("Runtime LLM config response is incomplete")
        return value

    def _context_from_request(self, request: Request) -> dict[str, str]:
        # These headers are overwritten by the API gateway after validating the
        # platform JWT. TIRE never trusts browser-provided identity on its own.
        mapping = {
            "x-auth-userid": "X-Context-User-Id",
            "x-customer-id": "X-Context-Customer-Id",
            "x-tenant-id": "X-Context-Tenant-Id",
        }
        result: dict[str, str] = {}
        for incoming, outgoing in mapping.items():
            value = (request.headers.get(incoming) or "").strip()
            if value:
                result[outgoing] = value
        return result

    def _for_local_user(
        self, value: dict[str, Any], local_user_id: int
    ) -> dict[str, Any]:
        result = dict(value)
        # A gateway response is the authoritative platform policy decision.
        # Downstream code must never substitute TIRE's process environment
        # when that decision contains no usable key (for example,
        # ``admin_locked`` or an unavailable assigned provider).
        result["allow_environment_fallback"] = False
        result["runtime_config_status"] = "available"
        # TIRE's usage database references its own local users. Platform identity
        # is used only by the gateway resolver and must not become a local FK.
        result["user_id"] = local_user_id
        provider_id = result.pop("provider_id", None)
        if provider_id is not None:
            result["platform_provider_id"] = provider_id
        return result

    @staticmethod
    def _fail_closed(local_user_id: int) -> dict[str, Any]:
        """Return a template-only decision when platform policy is unreachable.

        This is deliberately a concrete decision rather than ``None``.  The
        latter means the runtime bridge is disabled and enables legacy
        resolution; conflating the states could bypass an unseen platform lock.
        """
        return {
            "configured": False,
            "api_key": "",
            "model": "",
            "base_url": "",
            "source": "runtime_unavailable",
            "fingerprint": "runtime-unavailable",
            "version": 0,
            "user_id": local_user_id,
            "allow_environment_fallback": False,
            "allow_template_fallback": True,
            "runtime_config_status": "unavailable",
        }


runtime_llm_config = RuntimeLlmConfigClient()
