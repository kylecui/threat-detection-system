"""
IP profile model for normalized threat intelligence data.
"""

# pyright: reportMissingImports=false, reportImplicitRelativeImport=false

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel
from datetime import datetime
from typing import Optional, List, Dict, Any


class IPProfile(BaseModel):
    """Normalized profile for IP address threat intelligence."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    ip: str
    version: int = 4  # IPv4 or IPv6
    asn: Optional[str] = None
    organization: Optional[str] = None
    country: Optional[str] = None
    network: Optional[str] = None
    rdns: List[str] = []
    hostnames: List[str] = []
    tags: List[str] = []
    sources: Dict[str, Any] = {}
    external_refs: Dict[str, Any] = {}
    timestamps: Dict[str, datetime] = {}
