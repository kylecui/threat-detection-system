"""
Evidence model for threat intelligence analysis results.
"""

# pyright: reportMissingImports=false, reportImplicitRelativeImport=false

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel
from typing import Dict, Any


class EvidenceItem(BaseModel):
    """Individual piece of evidence from threat intelligence analysis."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    source: str
    category: str
    severity: str
    title: str
    detail: str
    score_delta: int = 0
    confidence: float = 0.0
    raw: Dict[str, Any] = {}
