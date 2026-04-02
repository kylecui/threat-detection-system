"""
Verdict engine for final risk assessment and decision making.
"""

import logging
import yaml
import os
from typing import List, Dict, Any, Optional
from models import Verdict, EvidenceItem

logger = logging.getLogger(__name__)


class VerdictEngine:
    """Generates final verdicts from analysis results."""

    def __init__(self):
        self.action_rules = self._load_action_rules()

    def _load_action_rules(self) -> Dict[str, Any]:
        """Load action rules from YAML file."""
        rules_path = os.path.join(
            os.path.dirname(__file__), "..", "rules", "action_rules.yaml"
        )
        try:
            with open(rules_path, "r", encoding="utf-8") as f:
                return yaml.safe_load(f)
        except Exception as e:
            logger.error(f"Failed to load action rules: {e}")
            # Fallback defaults
            return {
                "score_thresholds": {
                    "Low": [0, 20],
                    "Medium": [21, 45],
                    "High": [46, 75],
                    "Critical": [76, 100],
                },
                "decisions": {
                    "Low": "allow_with_monitoring",
                    "Medium": "investigate",
                    "High": "alert_and_review",
                    "Critical": "contain_or_block",
                    "Inconclusive": "collect_more_context",
                },
                "summaries": {
                    "Low": "{object_type} {object_value} shows minimal threat indicators. Final score: {final_score}/100. Safe for normal operations with monitoring.",
                    "Medium": "{object_type} {object_value} has moderate threat indicators. Final score: {final_score}/100. Recommend investigation before allowing.",
                    "High": "{object_type} {object_value} shows significant malicious activity. Final score: {final_score}/100. Alert and review required.",
                    "Critical": "{object_type} {object_value} shows critical threat indicators. Final score: {final_score}/100. Immediate containment recommended.",
                    "Inconclusive": "{object_type} {object_value} has conflicting evidence. Final score: {final_score}/100. More context needed for accurate assessment.",
                },
            }

    def generate_verdict(
        self,
        object_type: str,
        object_value: str,
        reputation_score: int,
        contextual_score: int,
        evidence: List[EvidenceItem],
        tags: Optional[List[str]] = None,
        raw_sources: Optional[Dict[str, Any]] = None,
    ) -> Verdict:
        """
        Generate final verdict from analysis results.

        Args:
            object_type: Type of object (ip, domain, etc.)
            object_value: The object value
            reputation_score: Score from reputation analysis
            contextual_score: Score from contextual analysis
            evidence: List of evidence items
            tags: Semantic tags
            raw_sources: Raw collector data from IPProfile.sources

        Returns:
            Final Verdict
        """
        # Calculate final score
        final_score = reputation_score + contextual_score
        final_score = max(0, min(100, final_score))  # Clamp to 0-100

        # Determine level
        level = self._determine_level(final_score, evidence)

        # Get decision
        decision = self.action_rules.get("decisions", {}).get(level, "investigate")

        # Calculate confidence
        confidence = self._calculate_confidence(evidence, final_score)

        # Generate summary
        summary = self._generate_summary(
            object_type, object_value, level, final_score, evidence
        )

        return Verdict(
            object_type=object_type,
            object_value=object_value,
            reputation_score=reputation_score,
            contextual_score=contextual_score,
            final_score=final_score,
            level=level,
            confidence=confidence,
            decision=decision,
            summary=summary,
            evidence=evidence,
            tags=tags or [],
            raw_sources=raw_sources or {},
        )

    def _determine_level(self, final_score: int, evidence: List[EvidenceItem]) -> str:
        """Determine verdict level based on score and evidence."""
        # Check for inconclusive conditions
        if self._should_be_inconclusive(evidence):
            return "Inconclusive"

        # Determine level by score
        thresholds = self.action_rules.get("score_thresholds", {})
        for level, (min_score, max_score) in thresholds.items():
            if min_score <= final_score <= max_score:
                return level

        # Fallback
        return "Medium"

    def _should_be_inconclusive(self, evidence: List[EvidenceItem]) -> bool:
        """Check if verdict should be inconclusive based on evidence conflicts."""
        # Simple heuristic: if we have conflicting evidence, be inconclusive
        positive_evidence = [e for e in evidence if e.score_delta > 0]
        negative_evidence = [e for e in evidence if e.score_delta < 0]

        # If we have both positive and negative evidence, it might be inconclusive
        if positive_evidence and negative_evidence:
            # Check if the evidence is truly conflicting
            has_strong_positive = any(e.score_delta >= 20 for e in positive_evidence)
            has_strong_negative = any(e.score_delta <= -15 for e in negative_evidence)

            if has_strong_positive and has_strong_negative:
                return True

        return False

    def _calculate_confidence(
        self, evidence: List[EvidenceItem], final_score: int
    ) -> float:
        """Calculate confidence in the verdict."""
        if not evidence:
            return 0.0

        # Base confidence on evidence strength and consistency
        total_confidence = sum(e.confidence for e in evidence if e.confidence > 0)
        avg_confidence = total_confidence / len(evidence) if evidence else 0.0

        # Adjust based on score magnitude
        score_factor = min(
            1.0, final_score / 50.0
        )  # Higher scores give higher confidence

        return min(1.0, (avg_confidence + score_factor) / 2.0)

    def _generate_summary(
        self,
        object_type: str,
        object_value: str,
        level: str,
        final_score: int,
        evidence: List[EvidenceItem],
    ) -> str:
        """Generate human-readable summary."""
        summary_template = self.action_rules.get("summaries", {}).get(
            level,
            f"Analysis complete for {object_type} {object_value}. Verdict: {level}, Score: {final_score}/100.",
        )

        return summary_template.format(
            object_type=object_type.upper(),
            object_value=object_value,
            final_score=final_score,
        )
