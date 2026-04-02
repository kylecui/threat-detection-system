"""
Evidence model for threat intelligence analysis results.
"""

from pydantic import BaseModel
from typing import Dict, Any


class EvidenceItem(BaseModel):
    """Individual piece of evidence from threat intelligence analysis."""

    source: str
    category: str
    severity: str
    title: str
    detail: str
    score_delta: int = 0
    confidence: float = 0.0
    raw: Dict[str, Any] = {}
