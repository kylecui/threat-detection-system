"""
Markdown reporter for threat intelligence verdicts.
"""

import os
from typing import Callable, List
from models import Verdict, EvidenceItem
from app.i18n import i18n


class MarkdownReporter:
    """Generates Markdown reports from verdicts."""

    def generate(self, verdict: Verdict, lang: str = "en") -> str:
        """
        Generate Markdown report from verdict.

        Args:
            verdict: Analysis verdict
            lang: Language code ("en" or "zh")

        Returns:
            Markdown string representation
        """
        t = i18n.get_translator(lang)
        lines = []

        # Title
        lines.append(f"# {t('report.title')}")
        lines.append("")

        # Object Summary
        lines.append(f"## {t('report.object_summary')}")
        lines.append("")
        lines.append(f"- **{t('report.type')}**: {verdict.object_type.upper()}")
        lines.append(f"- **{t('report.value')}**: `{verdict.object_value}`")
        lines.append("")

        # Analysis Results
        lines.append(f"## {t('report.analysis_results')}")
        lines.append("")
        lines.append(f"- **{t('report.final_score')}**: {verdict.final_score}/100")
        lines.append(
            f"- **{t('report.reputation_score')}**: {verdict.reputation_score}"
        )
        lines.append(
            f"- **{t('report.contextual_score')}**: {verdict.contextual_score}"
        )
        lines.append(
            f"- **{t('report.verdict_level')}**: {self._format_level(verdict.level, t)}"
        )
        lines.append(f"- **{t('report.confidence')}**: {verdict.confidence:.2%}")
        lines.append(
            f"- **{t('report.recommended_action')}**: {t('decision.' + verdict.decision)}"
        )
        lines.append("")

        # Summary
        lines.append(f"## {t('report.summary')}")
        lines.append("")
        summary = t(
            f"summary.{verdict.level}",
            object_type=verdict.object_type.upper(),
            object_value=verdict.object_value,
            final_score=verdict.final_score,
        )
        lines.append(summary)
        lines.append("")

        # Semantic Tags
        if verdict.tags:
            lines.append(f"## {t('report.semantic_tags')}")
            lines.append("")
            for tag in verdict.tags:
                lines.append(f"- `{tag}`")
            lines.append("")

        # Evidence
        if verdict.evidence:
            lines.append(f"## {t('report.evidence_details')}")
            lines.append("")

            # Group evidence by source
            sources = {}
            for evidence in verdict.evidence:
                if evidence.source not in sources:
                    sources[evidence.source] = []
                sources[evidence.source].append(evidence)

            for source, evidences in sources.items():
                lines.append(f"### {source.upper()}")
                lines.append("")

                for evidence in evidences:
                    lines.append(f"**{evidence.title}**")
                    lines.append(f"- {t('report.severity')} {evidence.severity}")
                    lines.append(
                        f"- {t('report.score_impact')}: {evidence.score_delta:+d}"
                    )
                    lines.append(
                        f"- {t('report.confidence')}: {evidence.confidence:.2%}"
                    )
                    lines.append(f"- {t('report.details')}: {evidence.detail}")
                    lines.append("")

        # Footer
        lines.append("---")
        lines.append("")
        lines.append(f"*{t('report.footer')}*")

        return "\n".join(lines)

    def _format_level(self, level: str, t: Callable[..., str]) -> str:
        """Format verdict level with emoji indicators."""
        translated = t(f"level.{level}")
        icons = {
            "Low": f"🟢 {translated}",
            "Medium": f"🟡 {translated}",
            "High": f"🟠 {translated}",
            "Critical": f"🔴 {translated}",
            "Inconclusive": f"⚪ {translated}",
        }
        return icons.get(level, translated)
