"""
CLI reporter for threat intelligence verdicts.
"""

from rich.console import Console
from rich.table import Table
from rich.text import Text
from rich.panel import Panel
from typing import Callable, List
from models import Verdict, EvidenceItem
from app.i18n import i18n

console = Console()


class CLIReporter:
    """Generates CLI/console reports from verdicts."""

    def generate(self, verdict: Verdict, lang: str = "en") -> None:
        """
        Print formatted verdict to console.

        Args:
            verdict: Analysis verdict
            lang: Language code ("en" or "zh")
        """
        t = i18n.get_translator(lang)

        # Header
        title = f"🔍 {t('cli.analysis_title')}: {verdict.object_type.upper()} {verdict.object_value}"
        console.print(Panel.fit(title, style="bold blue"))
        console.print()

        # Key metrics
        self._print_key_metrics(verdict, t)
        console.print()

        # Summary
        self._print_summary(verdict, t)
        console.print()

        # Evidence table
        if verdict.evidence:
            self._print_evidence_table(verdict.evidence, t)
            console.print()

        # Tags
        if verdict.tags:
            self._print_tags(verdict.tags, t)
            console.print()

    def _print_key_metrics(self, verdict: Verdict, t: Callable[..., str]) -> None:
        """Print key analysis metrics."""
        table = Table(show_header=False, box=None)
        table.add_column(t("cli.metric"), style="cyan", no_wrap=True)
        table.add_column(t("cli.value"), style="white")

        table.add_row(t("report.final_score"), f"{verdict.final_score}/100")
        table.add_row(t("report.reputation_score"), str(verdict.reputation_score))
        table.add_row(t("report.contextual_score"), str(verdict.contextual_score))
        table.add_row(t("report.verdict_level"), self._format_level(verdict.level, t))
        table.add_row(t("report.confidence"), f"{verdict.confidence:.1%}")
        table.add_row(
            t("report.recommended_action"), self._format_decision(verdict.decision, t)
        )

        console.print(table)

    def _print_summary(self, verdict: Verdict, t: Callable[..., str]) -> None:
        """Print analysis summary."""
        summary = t(
            f"summary.{verdict.level}",
            object_type=verdict.object_type.upper(),
            object_value=verdict.object_value,
            final_score=verdict.final_score,
        )
        summary_panel = Panel(
            summary, title=f"📋 {t('report.summary')}", border_style="green"
        )
        console.print(summary_panel)

    def _print_evidence_table(
        self, evidence: List[EvidenceItem], t: Callable[..., str]
    ) -> None:
        """Print evidence in a table format."""
        table = Table(title=f"🔎 {t('cli.evidence_title')}")
        table.add_column(t("report.source"), style="magenta", no_wrap=True)
        table.add_column("Title", style="white", max_width=40)
        table.add_column(t("report.severity"), style="yellow")
        table.add_column(t("report.score_impact"), style="red")
        table.add_column(t("report.confidence"), style="blue")

        for item in evidence:
            score_delta = f"{item.score_delta:+d}" if item.score_delta != 0 else "0"
            confidence = f"{item.confidence:.1%}"

            table.add_row(
                item.source.upper(),
                item.title,
                item.severity.upper(),
                score_delta,
                confidence,
            )

        console.print(table)

        # Print evidence details
        for i, item in enumerate(evidence, 1):
            detail_panel = Panel(
                item.detail,
                title=t("cli.evidence_item", index=i, title=item.title),
                border_style="dim blue",
            )
            console.print(detail_panel)
            console.print()

    def _print_tags(self, tags: List[str], t: Callable[..., str]) -> None:
        """Print semantic tags."""
        tag_text = Text(f"🏷️  {t('cli.tags_label')}: ", style="bold cyan")
        tag_list = Text(", ".join(f"`{tag}`" for tag in tags), style="white")
        console.print(tag_text + tag_list)

    def _format_level(self, level: str, t: Callable[..., str]) -> str:
        """Format verdict level with color."""
        translated = t(f"level.{level}")
        colors = {
            "Low": f"[green]🟢 {translated}[/green]",
            "Medium": f"[yellow]🟡 {translated}[/yellow]",
            "High": f"[red]🟠 {translated}[/red]",
            "Critical": f"[red]🔴 {translated}[/red]",
            "Inconclusive": f"[white]⚪ {translated}[/white]",
        }
        return colors.get(level, translated)

    def _format_decision(self, decision: str, t: Callable[..., str]) -> str:
        """Format decision for readability."""
        formatted = t(f"decision.{decision}")

        # Color based on severity
        if decision in ["contain_or_block"]:
            return f"[red]{formatted}[/red]"
        elif decision in ["alert_and_review", "investigate"]:
            return f"[yellow]{formatted}[/yellow]"
        else:
            return f"[green]{formatted}[/green]"
