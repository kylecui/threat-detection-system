"""
Reputation engine for analyzing threat intelligence data.
"""

import logging
import yaml
import os
from typing import List, Tuple, Dict, Any
from models import IPProfile, EvidenceItem

logger = logging.getLogger(__name__)


class ReputationEngine:
    """Analyzes IP reputation from collected threat intelligence."""

    def __init__(self):
        self.scoring_rules = self._load_scoring_rules()

    def _load_scoring_rules(self) -> Dict[str, Any]:
        """Load scoring rules from YAML file."""
        rules_path = os.path.join(
            os.path.dirname(__file__), "..", "rules", "scoring_rules.yaml"
        )
        try:
            with open(rules_path, "r", encoding="utf-8") as f:
                return yaml.safe_load(f)
        except Exception as e:
            logger.error(f"Failed to load scoring rules: {e}")
            return {}

    def analyze(
        self,
        profile: IPProfile,
        plugin_evidence: List[EvidenceItem] | None = None,
    ) -> Tuple[int, List[EvidenceItem]]:
        """
        Analyze IP reputation and generate evidence.

        v2.0: When plugin_evidence is provided, source-specific scoring
        is skipped because each plugin already produced scored evidence.
        Only semantic tag adjustments are applied here.

        When plugin_evidence is None (backward-compat / v1.0 mode),
        the old source-specific rule evaluation path is used.

        Args:
            profile: Normalized IP profile
            plugin_evidence: Pre-scored evidence from plugins (v2.0)

        Returns:
            Tuple of (reputation_score, evidence_list)
        """
        score = 0
        evidence = []

        if plugin_evidence is not None:
            # v2.0 path — plugins already scored; skip source-specific rules
            logger.info("Using plugin-provided evidence (v2.0 path)")
        else:
            # v1.0 backward-compat path — evaluate source-specific rules
            sources = profile.sources or {}

            for src_name, src_data in sources.items():
                is_ok = (
                    src_data.get("ok", False) if isinstance(src_data, dict) else False
                )
                logger.info(f"Source '{src_name}': ok={is_ok}")

            for source_name in self.scoring_rules.get("sources", {}):
                score_delta, src_evidence = self._analyze_source(
                    sources.get(source_name, {}), source_name
                )
                score += score_delta
                evidence.extend(src_evidence)

        # Apply semantic adjustments (always — both v1 and v2)
        if profile.tags:
            score_delta, semantic_evidence = self._apply_semantic_adjustments(
                profile.tags
            )
            score += score_delta
            evidence.extend(semantic_evidence)

        # Clamp score to 0-100
        score = max(0, min(100, score))

        return score, evidence

    def _analyze_source(
        self, source_data: Dict[str, Any], source_name: str
    ) -> Tuple[int, List[EvidenceItem]]:
        """Analyze a specific source using rules."""
        score = 0
        evidence = []

        if not source_data.get("ok", False):
            logger.debug(f"Skipping source '{source_name}': ok=False or missing")
            return score, evidence

        data = source_data.get("data", {})
        if not data:
            logger.debug(f"Skipping source '{source_name}': no data")
            return score, evidence

        source_rules = (
            self.scoring_rules.get("sources", {}).get(source_name, {}).get("rules", [])
        )

        if not source_rules:
            logger.debug(f"No scoring rules defined for source '{source_name}'")
            return score, evidence

        logger.debug(
            f"Evaluating {len(source_rules)} rules for '{source_name}' with data keys: {list(data.keys())}"
        )

        for rule in source_rules:
            condition = rule.get("condition", "")
            matched = self._evaluate_condition(condition, data)
            logger.debug(f"Rule '{source_name}' condition '{condition}' → {matched}")
            if matched:
                score_delta = self._calculate_score_delta(
                    rule.get("score_delta", 0), data
                )
                evidence.append(
                    EvidenceItem(
                        source=source_name,
                        category="reputation",
                        severity=rule.get("severity", "low"),
                        title=rule.get("title", ""),
                        detail=self._format_detail(
                            rule.get("detail_template", ""), data
                        ),
                        score_delta=score_delta,
                        confidence=self._calculate_confidence(data),
                    )
                )
                score += score_delta

        return score, evidence

    def _evaluate_condition(self, condition: str, data: Dict[str, Any]) -> bool:
        """Evaluate a condition string against data."""
        try:
            # Simple evaluation for now - in production, use a safer expression evaluator
            safe_locals = {
                k: v for k, v in data.items() if isinstance(v, (int, float, str, bool))
            }
            return eval(condition, {"__builtins__": {}}, safe_locals)
        except Exception:
            return False

    def _calculate_score_delta(self, delta_expr: Any, data: Dict[str, Any]) -> int:
        """Calculate score delta, handling expressions."""
        if isinstance(delta_expr, str):
            try:
                safe_locals = {
                    k: v
                    for k, v in data.items()
                    if isinstance(v, (int, float, str, bool))
                }
                return int(
                    eval(
                        delta_expr,
                        {"__builtins__": {"min": min, "max": max}},
                        safe_locals,
                    )
                )
            except Exception:
                return 0
        elif isinstance(delta_expr, int):
            return delta_expr
        return 0

    def _format_detail(self, template: str, data: Dict[str, Any]) -> str:
        """Format detail string with data."""
        try:
            return template.format(**data)
        except Exception:
            return template

    def _calculate_confidence(self, data: Dict[str, Any]) -> float:
        """Calculate confidence based on data."""
        # Simple heuristic - can be enhanced
        if "confidence" in data:
            return min(1.0, data["confidence"] / 100.0)
        elif "abuse_confidence_score" in data:
            return min(1.0, data["abuse_confidence_score"] / 100.0)
        return 0.5

    def _apply_semantic_adjustments(
        self, tags: List[str]
    ) -> Tuple[int, List[EvidenceItem]]:
        """Apply score adjustments based on semantic tags."""
        score = 0
        evidence = []

        adjustments = self.scoring_rules.get("semantic_adjustments", {})

        for tag in tags:
            if tag in adjustments:
                rule = adjustments[tag]
                delta = rule.get("score_delta", 0)
                score += delta
                evidence.append(
                    EvidenceItem(
                        source="semantic",
                        category="adjustment",
                        severity=rule.get("severity", "low"),
                        title=rule.get("title", ""),
                        detail=rule.get("detail_template", "").replace("_", " "),
                        score_delta=delta,
                        confidence=0.9,
                    )
                )

        return score, evidence
