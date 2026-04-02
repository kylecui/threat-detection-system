"""
LLM client for narrative report generation.

Uses OpenAI-compatible API. Supports any provider that implements
the OpenAI chat completions interface (OpenAI, Azure, Ollama, etc.).

If LLM_API_KEY is not configured, the client is disabled and all
generate() calls return None (triggering template-only fallback).

Per-user overrides: generate() accepts optional api_key/model/base_url
so each user's admin-portal settings take effect at request time.
"""

import logging
import asyncio
import random
import time
from typing import Any, Dict, Optional

import httpx

from app.config import settings
from storage.result_store import get_result_store

logger = logging.getLogger(__name__)


class LLMClient:
    """Async LLM client for generating narrative analysis paragraphs."""

    def __init__(self):
        self.api_key = settings.llm_api_key
        self.model = settings.llm_model
        self.base_url = settings.llm_base_url.rstrip("/")

    @property
    def enabled(self) -> bool:
        """Check if LLM is configured and available."""
        return bool(self.api_key)

    def is_enabled(self, overrides: Optional[Dict[str, Any]] = None) -> bool:
        """Check if LLM is available, considering optional per-user overrides."""
        if overrides and overrides.get("api_key"):
            return True
        return self.enabled

    async def generate(
        self,
        system_prompt: str,
        user_prompt: str,
        temperature: float = 0.3,
        max_tokens: int = 2000,
        *,
        api_key: Optional[str] = None,
        model: Optional[str] = None,
        base_url: Optional[str] = None,
        user_id: Optional[int] = None,
        source: str = "template",
        fingerprint: str = "",
        shared_config_id: int | None = None,
    ) -> Optional[str]:
        """
        Generate text using LLM API.

        Args:
            system_prompt: System role instructions
            user_prompt: User message with data and instructions
            temperature: Sampling temperature (lower = more deterministic)
            max_tokens: Maximum tokens in response
            api_key: Per-request override (from user's admin settings)
            model: Per-request override
            base_url: Per-request override

        Returns:
            Generated text, or None if LLM is unavailable/failed
        """
        effective_key = api_key or self.api_key
        effective_model = model or self.model
        effective_base = (base_url or self.base_url).rstrip("/")

        if not effective_key:
            logger.debug("LLM not configured, skipping generation")
            return None

        timeout = httpx.Timeout(connect=10.0, read=180.0, write=30.0, pool=10.0)
        max_attempts = 3
        last_reason = "unknown"

        for attempt in range(1, max_attempts + 1):
            started_at = time.perf_counter()
            try:
                async with httpx.AsyncClient(timeout=timeout) as client:
                    response = await client.post(
                        f"{effective_base}/chat/completions",
                        headers={
                            "Authorization": f"Bearer {effective_key}",
                            "Content-Type": "application/json",
                        },
                        json={
                            "model": effective_model,
                            "messages": [
                                {"role": "system", "content": system_prompt},
                                {"role": "user", "content": user_prompt},
                            ],
                            "temperature": temperature,
                            "max_tokens": max_tokens,
                        },
                    )
                    response.raise_for_status()
                    data = response.json()
                    content = data["choices"][0]["message"]["content"]
                    usage = data.get("usage", {})
                    prompt_tokens = int(usage.get("prompt_tokens", 0) or 0)
                    completion_tokens = int(usage.get("completion_tokens", 0) or 0)
                    total_tokens = int(usage.get("total_tokens", 0) or 0)
                    latency_ms = int((time.perf_counter() - started_at) * 1000)
                    get_result_store().record_llm_usage(
                        user_id=user_id,
                        source=source,
                        fingerprint=fingerprint,
                        shared_config_id=shared_config_id,
                        model=effective_model,
                        base_url=effective_base,
                        success=True,
                        latency_ms=latency_ms,
                        input_tokens=prompt_tokens,
                        output_tokens=completion_tokens,
                        total_tokens=total_tokens,
                        status_code=response.status_code,
                        request_id=response.headers.get("x-request-id", ""),
                    )
                    logger.info(
                        "LLM generation completed (attempt=%d, tokens=%d, model=%s, base=%s, request_id=%s)",
                        attempt,
                        total_tokens,
                        effective_model,
                        effective_base,
                        response.headers.get("x-request-id", ""),
                    )
                    return content

            except httpx.TimeoutException:
                last_reason = "timeout"
                get_result_store().record_llm_usage(
                    user_id=user_id,
                    source=source,
                    fingerprint=fingerprint,
                    shared_config_id=shared_config_id,
                    model=effective_model,
                    base_url=effective_base,
                    success=False,
                    latency_ms=int((time.perf_counter() - started_at) * 1000),
                    error_message="timeout",
                )
                logger.warning(
                    "LLM request timed out (attempt=%d/%d, model=%s, base=%s)",
                    attempt,
                    max_attempts,
                    effective_model,
                    effective_base,
                )
            except httpx.HTTPStatusError as e:
                status_code = e.response.status_code
                last_reason = f"http_{status_code}"
                get_result_store().record_llm_usage(
                    user_id=user_id,
                    source=source,
                    fingerprint=fingerprint,
                    shared_config_id=shared_config_id,
                    model=effective_model,
                    base_url=effective_base,
                    success=False,
                    latency_ms=int((time.perf_counter() - started_at) * 1000),
                    status_code=status_code,
                    error_message=e.response.text[:200],
                    request_id=e.response.headers.get("x-request-id", ""),
                )
                logger.warning(
                    "LLM API error (attempt=%d/%d, status=%s, model=%s, base=%s, request_id=%s): %s",
                    attempt,
                    max_attempts,
                    status_code,
                    effective_model,
                    effective_base,
                    e.response.headers.get("x-request-id", ""),
                    e.response.text[:200],
                )
                if status_code not in {429, 500, 502, 503, 504}:
                    break
            except Exception as e:
                last_reason = type(e).__name__
                get_result_store().record_llm_usage(
                    user_id=user_id,
                    source=source,
                    fingerprint=fingerprint,
                    shared_config_id=shared_config_id,
                    model=effective_model,
                    base_url=effective_base,
                    success=False,
                    latency_ms=int((time.perf_counter() - started_at) * 1000),
                    error_message=str(e),
                )
                logger.warning(
                    "LLM generation failed (attempt=%d/%d, model=%s, base=%s): %s",
                    attempt,
                    max_attempts,
                    effective_model,
                    effective_base,
                    e,
                )
                break

            if attempt < max_attempts:
                await asyncio.sleep(
                    min(1.0 * (2 ** (attempt - 1)), 8.0) * random.uniform(0.75, 1.25)
                )

        logger.warning(
            "LLM generation exhausted retries; falling back to template mode (reason=%s, model=%s, base=%s)",
            last_reason,
            effective_model,
            effective_base,
        )
        return None

    async def validate_connection(
        self,
        api_key: str,
        base_url: str,
    ) -> Dict[str, Any]:
        """Test an API key + base_url by listing available models.

        Returns:
            {"ok": bool, "models": list[str], "error": str | None}
        """
        effective_base = base_url.rstrip("/")
        try:
            async with httpx.AsyncClient(timeout=15.0) as client:
                response = await client.get(
                    f"{effective_base}/models",
                    headers={"Authorization": f"Bearer {api_key}"},
                )
                response.raise_for_status()
                data = response.json()
                models = sorted([m["id"] for m in data.get("data", []) if m.get("id")])
                return {"ok": True, "models": models, "error": None}
        except httpx.TimeoutException:
            return {"ok": False, "models": [], "error": "Connection timed out"}
        except httpx.HTTPStatusError as e:
            return {
                "ok": False,
                "models": [],
                "error": f"HTTP {e.response.status_code}: {e.response.text[:200]}",
            }
        except Exception as e:
            return {"ok": False, "models": [], "error": str(e)}


# Global instance
llm_client = LLMClient()
