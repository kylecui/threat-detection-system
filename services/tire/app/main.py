"""
CLI interface for Threat Intelligence Reasoning Engine.
"""

import asyncio
from typing import Optional
from pathlib import Path
import typer
from app.service import ThreatIntelService
from app.i18n import i18n
from adapters.csv_adapter import CSVAdapter
from reporters.json_reporter import JSONReporter
from reporters.markdown_reporter import MarkdownReporter
from reporters.cli_reporter import CLIReporter

app = typer.Typer(help="Threat Intelligence Reasoning Engine CLI")
service = ThreatIntelService()


@app.command()
def lookup(
    ip: str = typer.Argument(..., help="IP address to lookup"),
    format: str = typer.Option("cli", help="Output format: cli, json, md"),
    refresh: bool = typer.Option(False, help="Skip cache and refresh data"),
    lang: str = typer.Option("en", help="Display language: en or zh"),
):
    """Quick lookup of an IP address."""
    verdict = asyncio.run(service.analyze_ip(ip, refresh=refresh))

    if format == "json":
        reporter = JSONReporter()
        output = reporter.generate(verdict)
    elif format == "md":
        reporter = MarkdownReporter()
        output = reporter.generate(verdict, lang=lang)
    else:  # cli
        reporter = CLIReporter()
        reporter.generate(verdict, lang=lang)
        return  # CLI reporter prints directly

    typer.echo(output)


@app.command()
def report(
    ip: str = typer.Argument(..., help="IP address to analyze"),
    format: str = typer.Option("md", help="Output format: json, md"),
    output: Optional[Path] = typer.Option(None, help="Output file path"),
    refresh: bool = typer.Option(False, help="Skip cache and refresh data"),
    lang: str = typer.Option("en", help="Display language: en or zh"),
):
    """Generate detailed report for an IP address."""
    verdict = asyncio.run(service.analyze_ip(ip, refresh=refresh))

    if format == "json":
        reporter = JSONReporter()
        content = reporter.generate(verdict)
    elif format == "md":
        reporter = MarkdownReporter()
        content = reporter.generate(verdict, lang=lang)
    else:
        typer.echo("Invalid format. Use 'json' or 'md'", err=True)
        raise typer.Exit(1)

    if output:
        output.write_text(content, encoding="utf-8")
        t = i18n.get_translator(lang)
        typer.echo(t("cli.report_saved").format(path=output))
    else:
        typer.echo(content)


@app.command()
def analyze(
    ip: str = typer.Argument(..., help="IP address to analyze"),
    port: Optional[int] = typer.Option(None, help="Port number"),
    direction: Optional[str] = typer.Option(
        None, help="Traffic direction (inbound/outbound)"
    ),
    hostname: Optional[str] = typer.Option(None, help="Associated hostname"),
    protocol: Optional[str] = typer.Option(None, help="Protocol (tcp/udp)"),
    process_name: Optional[str] = typer.Option(None, help="Process name"),
    host_role: Optional[str] = typer.Option(
        None, help="Host role (workstation/server)"
    ),
    format: str = typer.Option("cli", help="Output format: cli, json, md"),
    refresh: bool = typer.Option(False, help="Skip cache and refresh data"),
    lang: str = typer.Option("en", help="Display language: en or zh"),
):
    """Context-aware analysis with additional behavioral context."""
    from models import ContextProfile

    # Build context if any parameters provided
    context = None
    if any([port, direction, hostname, protocol, process_name, host_role]):
        context = ContextProfile(
            direction=direction,
            protocol=protocol,
            port=port,
            hostname=hostname,
            process_name=process_name,
            host_role=host_role,
        )

    verdict = asyncio.run(service.analyze_ip(ip, context, refresh=refresh))

    if format == "json":
        reporter = JSONReporter()
        output = reporter.generate(verdict)
    elif format == "md":
        reporter = MarkdownReporter()
        output = reporter.generate(verdict, lang=lang)
    else:  # cli
        reporter = CLIReporter()
        reporter.generate(verdict, lang=lang)
        return

    typer.echo(output)


@app.command()
def batch(
    input_file: Path = typer.Argument(..., help="Input CSV file with observables"),
    format: str = typer.Option("json", help="Output format: json, md"),
    output: Optional[Path] = typer.Option(None, help="Output file path"),
    refresh: bool = typer.Option(False, help="Skip cache and refresh data"),
    lang: str = typer.Option("en", help="Display language: en or zh"),
):
    """Batch analysis of multiple observables."""
    t = i18n.get_translator(lang)
    csv_adapter = CSVAdapter(service)

    results = asyncio.run(
        csv_adapter.process_batch(input_file, output, refresh=refresh)
    )

    if not output:
        # Print summary to console
        successful = sum(1 for r in results if r["success"])
        total = len(results)
        typer.echo(t("cli.batch_summary").format(total=total, successful=successful))

        if format == "json":
            import json

            typer.echo(json.dumps(results, indent=2))
        else:
            # Simple markdown summary
            typer.echo(t("cli.batch_title"))
            typer.echo(t("cli.batch_stats").format(total=total, successful=successful))
            for result in results:
                if result["success"] and result["verdict"]:
                    v = result["verdict"]
                    level = v.get("level", "Unknown")
                    level_key = f"level.{level}"
                    level_label = t(level_key)
                    if level_label == level_key:
                        level_label = level
                    typer.echo(
                        f"- {result['type']}:{result['value']} - {level_label} ({v.get('final_score', 0)})"
                    )
                else:
                    typer.echo(
                        f"- {result['type']}:{result['value']} - ERROR: {result.get('error', 'Unknown')}"
                    )
    else:
        typer.echo(t("cli.results_saved").format(path=output))


if __name__ == "__main__":
    app()
