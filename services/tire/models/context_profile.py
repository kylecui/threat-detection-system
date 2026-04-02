"""
Context profile model for behavioral analysis context.
"""

from pydantic import BaseModel
from datetime import datetime
from typing import Optional


class ContextProfile(BaseModel):
    """Context information for observable analysis."""

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
