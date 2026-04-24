"""
Context profile model for behavioral analysis context.
"""

# pyright: reportMissingImports=false, reportImplicitRelativeImport=false

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel
from datetime import datetime
from typing import Optional


class ContextProfile(BaseModel):
    """Context information for observable analysis."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    direction: Optional[str] = None
    protocol: Optional[str] = None
    port: Optional[int] = None
    hostname: Optional[str] = None
    sni: Optional[str] = None
    process_name: Optional[str] = None
    process_path: Optional[str] = None
    host_role: Optional[str] = None
    timestamp: Optional[datetime] = None
    src_ip: Optional[str] = None
    dst_ip: Optional[str] = None
