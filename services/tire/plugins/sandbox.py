"""
Sandboxed plugin runner — executes untrusted plugins in isolated subprocesses.

Each sandboxed plugin query spawns a new Python subprocess that:
    - Has its own memory space (crash isolation)
    - Receives only allowed environment variables (API key isolation)
    - Is killed on timeout (hang protection)
    - Has memory limits on Linux (resource exhaustion protection)

Builtin (trusted) plugins bypass the sandbox and run in-process for zero overhead.

Usage:
    runner = SandboxedPluginRunner()
    result = await runner.run(plugin, observable, obs_type, sandbox_config)
"""

import asyncio
import json
import logging
import os
import platform
import sys
from pathlib import Path
from typing import Any

from models import EvidenceItem
from plugins.base import PluginResult, TIPlugin

logger = logging.getLogger(__name__)

# Path to the subprocess worker script
_WORKER_SCRIPT = str(Path(__file__).parent / "_subprocess_worker.py")

# Project root directory (for subprocess cwd)
_PROJECT_ROOT = str(Path(__file__).parent.parent)

# System environment variables that are always safe to pass
_SYSTEM_ENV_KEYS = {
    "PATH",
    "PYTHONPATH",
    "HOME",
    "USERPROFILE",  # Windows home
    "SYSTEMROOT",  # Windows system root
    "TEMP",
    "TMP",
    "LANG",
    "LC_ALL",
    "SSL_CERT_FILE",  # needed for HTTPS requests
    "SSL_CERT_DIR",
    "REQUESTS_CA_BUNDLE",
}


class SandboxedPluginRunner:
    """Runs plugin queries in isolated subprocesses."""

    async def run(
        self,
        plugin: TIPlugin,
        observable: str,
        obs_type: str,
        sandbox_config: dict[str, Any],
    ) -> PluginResult:
        """Execute a plugin query in a sandboxed subprocess.

        Args:
            plugin: The plugin instance (used for metadata/config, NOT executed in-process)
            observable: The value to query (e.g., "8.8.8.8")
            obs_type: The observable type ("ip", "domain", etc.)
            sandbox_config: Sandbox settings (timeout, memory_limit_mb, etc.)

        Returns:
            PluginResult from the subprocess, or an error PluginResult on failure
        """
        meta = plugin.metadata
        timeout = sandbox_config.get("timeout", 30)
        memory_limit_mb = sandbox_config.get("memory_limit_mb", 512)

        # Build the request payload for the subprocess
        request = {
            "plugin_module": self._get_module_path(plugin),
            "plugin_class": type(plugin).__name__,
            "observable": observable,
            "obs_type": obs_type,
            "config": plugin.plugin_config,
            "env_vars": self._build_env_vars(plugin),
            "memory_limit_mb": memory_limit_mb,
        }

        try:
            result_data = await self._spawn_and_communicate(
                request, timeout=timeout, plugin_name=meta.name
            )
        except asyncio.TimeoutError:
            logger.error(
                "Plugin '%s' timed out after %ds in sandbox", meta.name, timeout
            )
            return PluginResult(
                source=meta.name,
                ok=False,
                error=f"Plugin timed out after {timeout}s",
            )
        except Exception as e:
            logger.error(
                "Sandbox execution failed for plugin '%s': %s",
                meta.name,
                e,
                exc_info=True,
            )
            return PluginResult(
                source=meta.name,
                ok=False,
                error=f"Sandbox execution failed: {e}",
            )

        # Parse the subprocess output
        return self._parse_result(result_data, meta.name)

    async def _spawn_and_communicate(
        self,
        request: dict,
        timeout: int,
        plugin_name: str,
    ) -> dict:
        """Spawn a subprocess, send request, and collect response."""
        # Build subprocess creation kwargs
        kwargs: dict[str, Any] = {
            "stdin": asyncio.subprocess.PIPE,
            "stdout": asyncio.subprocess.PIPE,
            "stderr": asyncio.subprocess.PIPE,
            "cwd": _PROJECT_ROOT,
        }

        # On Windows, prevent Ctrl+C propagation to child
        if platform.system() == "Windows":
            import subprocess as sp

            kwargs["creationflags"] = sp.CREATE_NEW_PROCESS_GROUP

        proc = await asyncio.create_subprocess_exec(
            sys.executable,
            _WORKER_SCRIPT,
            **kwargs,
        )

        try:
            input_data = json.dumps(request).encode("utf-8")
            stdout, stderr = await asyncio.wait_for(
                proc.communicate(input=input_data),
                timeout=timeout,
            )
        except asyncio.TimeoutError:
            # Kill the process and wait for cleanup
            try:
                proc.kill()
            except ProcessLookupError:
                pass  # Already dead
            await proc.wait()
            raise

        # Log subprocess stderr at DEBUG level
        if stderr:
            stderr_text = stderr.decode("utf-8", errors="replace").strip()
            if stderr_text:
                for line in stderr_text.split("\n"):
                    logger.debug("[sandbox:%s] %s", plugin_name, line)

        # Check exit code
        if proc.returncode != 0:
            stdout_text = stdout.decode("utf-8", errors="replace").strip()
            # Try to parse error from stdout (worker writes JSON errors)
            if stdout_text:
                try:
                    return json.loads(stdout_text)
                except json.JSONDecodeError:
                    pass
            stderr_text = stderr.decode("utf-8", errors="replace").strip()
            raise RuntimeError(
                f"Subprocess exited with code {proc.returncode}: "
                f"{stderr_text or stdout_text or 'no output'}"
            )

        # Parse stdout JSON
        stdout_text = stdout.decode("utf-8", errors="replace").strip()
        if not stdout_text:
            raise RuntimeError("Subprocess produced no output")

        try:
            return json.loads(stdout_text)
        except json.JSONDecodeError as e:
            raise RuntimeError(
                f"Invalid JSON from subprocess: {e}\nRaw output: {stdout_text[:500]}"
            )

    @staticmethod
    def _parse_result(data: dict, plugin_name: str) -> PluginResult:
        """Parse subprocess JSON output into a PluginResult."""
        if not data.get("ok", False):
            error_msg = data.get("error", "Unknown subprocess error")
            tb = data.get("traceback", "")
            if tb:
                logger.debug("Plugin '%s' traceback:\n%s", plugin_name, tb)
            return PluginResult(
                source=plugin_name,
                ok=False,
                error=error_msg,
            )

        r = data["result"]
        evidence = [EvidenceItem.model_validate(e) for e in r.get("evidence", [])]

        return PluginResult(
            source=r.get("source", plugin_name),
            ok=r.get("ok", True),
            raw_data=r.get("raw_data"),
            normalized_data=r.get("normalized_data"),
            evidence=evidence,
            error=r.get("error"),
        )

    @staticmethod
    def _build_env_vars(plugin: TIPlugin) -> dict[str, str]:
        """Build a restricted env var dict for the subprocess.

        Only passes:
            - System essentials (PATH, HOME, etc.)
            - The plugin's own API key env var (if declared)

        This prevents untrusted plugins from reading other plugins'
        API keys, LLM keys, session secrets, etc.
        """
        env_vars: dict[str, str] = {}

        # System essentials
        for key in _SYSTEM_ENV_KEYS:
            value = os.environ.get(key)
            if value is not None:
                env_vars[key] = value

        # Plugin's own API key override (preferred) or nothing.
        api_key_env = plugin.metadata.api_key_env_var
        if api_key_env:
            value = getattr(plugin, "_override_api_key", None)
            if value is not None:
                env_vars[api_key_env] = value

        return env_vars

    @staticmethod
    def _get_module_path(plugin: TIPlugin) -> str:
        """Get the fully qualified module path for a plugin class."""
        return type(plugin).__module__
