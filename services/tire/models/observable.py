"""
Core observable model for threat intelligence analysis.
"""

from pydantic import BaseModel
from typing import Literal


class Observable(BaseModel):
    """Represents an observable entity to be analyzed."""

    type: Literal["ip", "domain", "url"]
    value: str
