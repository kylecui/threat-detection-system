"""
Domain profile model for normalized threat intelligence data.
"""

# pyright: reportMissingImports=false, reportImplicitRelativeImport=false

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel
from datetime import datetime
from typing import Optional, List, Dict, Any


class DomainProfile(BaseModel):
    """Normalized profile for domain threat intelligence."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    domain: str
    apex_domain: Optional[str] = None
    registrar: Optional[str] = None
    created_at: Optional[datetime] = None
    resolved_ips: List[str] = []
    tags: List[str] = []
    sources: Dict[str, Any] = {}
