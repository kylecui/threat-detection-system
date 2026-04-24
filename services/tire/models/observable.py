"""
Core observable model for threat intelligence analysis.
"""

# pyright: reportMissingImports=false, reportImplicitRelativeImport=false

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel
from typing import Literal


class Observable(BaseModel):
    """Represents an observable entity to be analyzed."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    type: Literal["ip", "domain", "url"]
    value: str
