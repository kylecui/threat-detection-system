"""
Subprocess worker for sandboxed plugin execution.

This script runs inside a child process spawned by SandboxedPluginRunner.
It handles:
    1. Reading query parameters from stdin (JSON)
    2. Applying resource limits (Linux only)
    3. Importing and instantiating the plugin
    4. Running plugin.query() in an asyncio event loop
    5. Serializing the PluginResult to stdout (JSON)

Protocol:
    stdin  ← JSON: {"plugin_module", "plugin_class", "observable", "obs_type", "config", "env_vars"}
    stdout → JSON: {"ok": true, "result": {...}} or {"ok": false, "error": "...", "traceback": "..."}
    stderr → Plugin log output (captured by parent for debugging)

This file is prefixed with _ to prevent the plugin registry from
scanning it as a plugin module.
"""

import asyncio
import importlib
import inspect
import json
import logging
import os
import platform
import sys
import traceback

# Redirect ALL logging to stderr so stdout stays clean for JSON result
logging.basicConfig(
    stream=sys.stderr,
    level=logging.DEBUG,
    format="%(asctime)s %(levelname)s [sandbox:%(name)s] %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("subprocess_worker")


def _apply_resource_limits(memory_limit_mb: int) -> None:
    """Apply memory limits on Linux via resource.setrlimit.

    On Windows/macOS this is a no-op (documented limitation).
    """
    if platform.system() != "Linux":
        return

    try:
        import resource

        limit_bytes = memory_limit_mb * 1024 * 1024
        resource.setrlimit(resource.RLIMIT_AS, (limit_bytes, limit_bytes))
        logger.debug("Memory limit set to %d MB", memory_limit_mb)
    except Exception as e:
        logger.warning("Failed to set memory limit: %s", e)


def _serialize_result(plugin_result) -> dict:
    """Serialize a PluginResult (dataclass) to a JSON-safe dict.

    EvidenceItem objects are Pydantic models — use .model_dump().
    """
    return {
        "source": plugin_result.source,
        "ok": plugin_result.ok,
        "raw_data": plugin_result.raw_data,
        "normalized_data": plugin_result.normalized_data,
        "evidence": [e.model_dump() for e in plugin_result.evidence],
        "error": plugin_result.error,
    }


async def _run_plugin(request: dict) -> dict:
    """Import, instantiate, configure, and run a plugin query."""
    module_path = request["plugin_module"]
    class_name = request["plugin_class"]
    observable = request["observable"]
    obs_type = request["obs_type"]
    config = request.get("config", {})

    # Import the plugin module
    logger.debug("Importing %s.%s", module_path, class_name)
    module = importlib.import_module(module_path)

    # Find the plugin class
    plugin_class = getattr(module, class_name, None)
    if plugin_class is None:
        raise ImportError(f"Class '{class_name}' not found in module '{module_path}'")

    # Instantiate and configure
    plugin = plugin_class()
    plugin.configure(config)

    # Run the query
    logger.debug("Running query: %s (%s)", observable, obs_type)
    result = await plugin.query(observable, obs_type)

    return {"ok": True, "result": _serialize_result(result)}


def main() -> None:
    """Entry point for the subprocess worker."""
    try:
        # Read JSON request from stdin
        raw_input = sys.stdin.read()
        if not raw_input.strip():
            _write_error("Empty input on stdin")
            sys.exit(1)

        request = json.loads(raw_input)

        # Apply resource limits before importing untrusted code
        memory_limit_mb = request.get("memory_limit_mb", 512)
        _apply_resource_limits(memory_limit_mb)

        # Set environment variables (only the allowed ones passed by parent)
        env_vars = request.get("env_vars", {})
        for key, value in env_vars.items():
            os.environ[key] = value

        # Run the plugin query
        output = asyncio.run(_run_plugin(request))

        # Write result to stdout
        sys.stdout.write(json.dumps(output))
        sys.stdout.flush()

    except json.JSONDecodeError as e:
        _write_error(f"Invalid JSON input: {e}")
        sys.exit(1)
    except Exception as e:
        _write_error(str(e), tb=traceback.format_exc())
        sys.exit(1)


def _write_error(error: str, tb: str | None = None) -> None:
    """Write an error response to stdout."""
    output = {"ok": False, "error": error}
    if tb:
        output["traceback"] = tb
    sys.stdout.write(json.dumps(output))
    sys.stdout.flush()


if __name__ == "__main__":
    # Ensure project root is in sys.path so plugin imports work
    project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    if project_root not in sys.path:
        sys.path.insert(0, project_root)

    main()
