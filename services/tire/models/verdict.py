"""
Verdict model for final analysis results.
"""

# pyright: reportMissingImports=false, reportImplicitRelativeImport=false

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel
from typing import List, Optional, Dict, Any
from .evidence import EvidenceItem


class Verdict(BaseModel):
    """Final verdict from threat intelligence analysis."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    object_type: str
    object_value: str
    reputation_score: int
    contextual_score: int
    final_score: int
    level: str
    confidence: float
    decision: str
    summary: str
    evidence: List[EvidenceItem] = []
    tags: List[str] = []
    raw_sources: Dict[str, Any] = {}
