"""
TIRE v2.0 Plugin System.
"""

from plugins.base import TIPlugin, PluginMetadata, PluginResult
from plugins.registry import PluginRegistry
from plugins.sandbox import SandboxedPluginRunner

__all__ = [
    "TIPlugin",
    "PluginMetadata",
    "PluginResult",
    "PluginRegistry",
    "SandboxedPluginRunner",
]
