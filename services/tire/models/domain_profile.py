"""
Domain profile model for normalized threat intelligence data.
"""

from pydantic import BaseModel
from datetime import datetime
from typing import Optional, List, Dict, Any


class DomainProfile(BaseModel):
    """Normalized profile for domain threat intelligence."""

    domain: str
    apex_domain: Optional[str] = None
    registrar: Optional[str] = None
    created_at: Optional[datetime] = None
    resolved_ips: List[str] = []
    tags: List[str] = []
    sources: Dict[str, Any] = {}
