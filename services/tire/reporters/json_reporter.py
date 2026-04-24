"""
JSON reporter for threat intelligence verdicts.
"""

# pyright: reportMissingImports=false, reportImplicitRelativeImport=false

import json
from typing import Any, Dict
from models import Verdict


class JSONReporter:
    """Generates JSON reports from verdicts."""

    def generate(self, verdict: Verdict) -> str:
        """
        Generate JSON report from verdict.

        Args:
            verdict: Analysis verdict

        Returns:
            JSON string representation
        """
        report = {
            "object": {"type": verdict.object_type, "value": verdict.object_value},
            "analysis": {
                "reputationScore": verdict.reputation_score,
                "contextualScore": verdict.contextual_score,
                "finalScore": verdict.final_score,
                "level": verdict.level,
                "confidence": verdict.confidence,
                "decision": verdict.decision,
            },
            "summary": verdict.summary,
            "tags": verdict.tags,
            "evidence": [
                {
                    "source": e.source,
                    "category": e.category,
                    "severity": e.severity,
                    "title": e.title,
                    "detail": e.detail,
                    "scoreDelta": e.score_delta,
                    "confidence": e.confidence,
                    "rawData": e.raw,
                }
                for e in verdict.evidence
            ],
            "metadata": {
                "generatedBy": "Threat Intelligence Reasoning Engine",
                "version": "2.0.0",
            },
        }

        return json.dumps(report, indent=2, ensure_ascii=False)
