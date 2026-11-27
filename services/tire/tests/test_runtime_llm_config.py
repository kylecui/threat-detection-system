from __future__ import annotations

import unittest
from types import SimpleNamespace

from app.config import settings
from app.llm_client import LLMClient
from app.runtime_llm_config import RuntimeLlmConfigClient


class _Client(RuntimeLlmConfigClient):
    def __init__(self) -> None:
        super().__init__()
        self.calls = 0
        self.fail = False

    async def _fetch(self, context: dict[str, str]) -> dict[str, object]:
        self.calls += 1
        if self.fail:
            raise RuntimeError("gateway unavailable")
        return {
            "configured": True,
            "api_key": "sk-runtime",
            "model": "runtime-model",
            "base_url": "https://llm.invalid/v1",
            "source": "system_config",
            "fingerprint": "abc123",
        }


class RuntimeLlmConfigClientTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self) -> None:
        self.previous = (
            settings.llm_runtime_config_url,
            settings.llm_runtime_service_token,
            settings.llm_runtime_cache_ttl_seconds,
            settings.llm_runtime_stale_ttl_seconds,
        )
        settings.llm_runtime_config_url = "http://gateway/internal"
        settings.llm_runtime_service_token = "service-token"
        settings.llm_runtime_cache_ttl_seconds = 45
        settings.llm_runtime_stale_ttl_seconds = 3600

    def tearDown(self) -> None:
        (
            settings.llm_runtime_config_url,
            settings.llm_runtime_service_token,
            settings.llm_runtime_cache_ttl_seconds,
            settings.llm_runtime_stale_ttl_seconds,
        ) = self.previous

    async def test_caches_by_trusted_platform_context(self) -> None:
        client = _Client()
        request = SimpleNamespace(
            headers={
                "x-auth-userid": "42",
                "x-customer-id": "customer-a",
                "x-tenant-id": "7",
            }
        )

        first = await client.resolve(request, local_user_id=3)
        second = await client.resolve(request, local_user_id=3)

        self.assertEqual(1, client.calls)
        self.assertEqual("sk-runtime", first["api_key"])
        self.assertEqual(3, second["user_id"])
        self.assertFalse(first["allow_environment_fallback"])
        self.assertEqual("available", first["runtime_config_status"])

    async def test_uses_last_valid_value_when_refresh_fails(self) -> None:
        client = _Client()
        request = SimpleNamespace(headers={})
        first = await client.resolve(request, local_user_id=1)
        client._cache[("", "", "")].fetched_at -= 60
        client.fail = True

        stale = await client.resolve(request, local_user_id=1)

        self.assertEqual(first["fingerprint"], stale["fingerprint"])
        self.assertEqual(2, client.calls)

    async def test_fails_closed_without_cache_when_configured_gateway_fails(self) -> None:
        client = _Client()
        client.fail = True
        request = SimpleNamespace(headers={})

        resolved = await client.resolve(request, local_user_id=9)

        self.assertIsNotNone(resolved)
        self.assertFalse(resolved["configured"])
        self.assertEqual("runtime_unavailable", resolved["source"])
        self.assertEqual("unavailable", resolved["runtime_config_status"])
        self.assertFalse(resolved["allow_environment_fallback"])
        self.assertEqual(9, resolved["user_id"])

    async def test_missing_bridge_configuration_preserves_legacy_mode(self) -> None:
        client = _Client()
        request = SimpleNamespace(headers={})
        settings.llm_runtime_config_url = None
        settings.llm_runtime_service_token = None

        resolved = await client.resolve(request, local_user_id=1)

        self.assertIsNone(resolved)
        self.assertEqual(0, client.calls)

    async def test_partial_bridge_configuration_fails_closed(self) -> None:
        client = _Client()
        request = SimpleNamespace(headers={})
        settings.llm_runtime_service_token = None

        resolved = await client.resolve(request, local_user_id=1)

        self.assertEqual("runtime_unavailable", resolved["source"])
        self.assertFalse(resolved["allow_environment_fallback"])
        self.assertEqual(0, client.calls)

    async def test_authoritative_empty_decision_cannot_use_environment_key(self) -> None:
        client = LLMClient()
        client.api_key = "sk-legacy-environment"
        decision = {
            "api_key": "",
            "source": "admin_locked",
            "allow_environment_fallback": False,
        }

        self.assertFalse(client.is_enabled(decision))
        generated = await client.generate(
            "system",
            "user",
            allow_environment_fallback=False,
        )
        self.assertIsNone(generated)


if __name__ == "__main__":
    unittest.main()
