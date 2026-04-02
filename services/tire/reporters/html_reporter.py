"""HTML reporter for threat intelligence analysis results."""

import os
from typing import Dict, Any
from jinja2 import Environment, FileSystemLoader
from models import Verdict
from app.i18n import i18n


class HTMLReporter:
    """Generates HTML reports from verdict data."""

    SOURCE_DISPLAY_NAMES = {
        "rdap": "RDAP",
        "reverse_dns": "Reverse DNS",
        "virustotal": "VirusTotal",
        "abuseipdb": "AbuseIPDB",
        "shodan": "Shodan",
        "threatbook": "ThreatBook",
        "tianjiyoumeng": "TianJi YouMeng",
        "greynoise": "GreyNoise",
        "otx": "AlienVault OTX",
        "honeynet": "Honeynet",
        "internal_flow": "Internal Flow",
    }

    def __init__(self):
        template_dir = os.path.join(os.path.dirname(__file__), "..", "templates")
        self.env = Environment(loader=FileSystemLoader(template_dir))

    def generate(self, verdict: Verdict, lang: str = "en") -> str:
        """
        Generate HTML report from verdict.

        Args:
            verdict: Analysis verdict
            lang: Language code ("en" or "zh")

        Returns:
            HTML string
        """
        template = self.env.get_template("report.html.j2")

        # Prepare data for template
        data = self._prepare_template_data(verdict, lang)

        return template.render(**data)

    def _prepare_template_data(
        self, verdict: Verdict, lang: str = "en"
    ) -> Dict[str, Any]:
        """Prepare data for HTML template."""
        # Group evidence by category and severity
        evidence_by_category = {}
        for evidence in verdict.evidence:
            category = evidence.category
            if category not in evidence_by_category:
                evidence_by_category[category] = []
            evidence_by_category[category].append(evidence)

        # Sort evidence by score_delta descending
        for category in evidence_by_category:
            evidence_by_category[category].sort(
                key=lambda e: e.score_delta, reverse=True
            )

        raw_sources = verdict.raw_sources or {}
        rdap = raw_sources.get("rdap", {}) if isinstance(raw_sources, dict) else {}
        reverse_dns = (
            raw_sources.get("reverse_dns", {}) if isinstance(raw_sources, dict) else {}
        )
        virustotal = (
            raw_sources.get("virustotal", {}) if isinstance(raw_sources, dict) else {}
        )
        abuseipdb = (
            raw_sources.get("abuseipdb", {}) if isinstance(raw_sources, dict) else {}
        )
        shodan = raw_sources.get("shodan", {}) if isinstance(raw_sources, dict) else {}

        rdap_data = rdap.get("data", {}) if isinstance(rdap, dict) else {}
        rdns_data = reverse_dns.get("data", {}) if isinstance(reverse_dns, dict) else {}
        vt_data = virustotal.get("data", {}) if isinstance(virustotal, dict) else {}
        abuse_data = abuseipdb.get("data", {}) if isinstance(abuseipdb, dict) else {}
        shodan_data = shodan.get("data", {}) if isinstance(shodan, dict) else {}

        ownership_fields = [
            {"label": "narrative.organization", "value": rdap_data.get("name")},
            {"label": "narrative.asn", "value": rdap_data.get("asn")},
            {"label": "narrative.country", "value": rdap_data.get("country")},
            {"label": "narrative.network", "value": rdap_data.get("network")},
            {"label": "narrative.reverse_dns", "value": rdns_data.get("hostname")},
            {
                "label": "narrative.aliases",
                "value": ", ".join(rdns_data.get("aliases", []))
                if rdns_data.get("aliases")
                else None,
            },
            {
                "label": "narrative.related_domains",
                "value": ", ".join(vt_data.get("related_domains", []))
                if vt_data.get("related_domains")
                else None,
            },
            {"label": "narrative.isp", "value": abuse_data.get("isp")},
            {"label": "narrative.usage_type", "value": abuse_data.get("usageType")},
            {
                "label": "narrative.ports",
                "value": ", ".join(map(str, shodan_data.get("ports", [])))
                if shodan_data.get("ports")
                else None,
            },
        ]

        source_status = self._build_source_status(raw_sources)

        return {
            "verdict": verdict,
            "evidence_by_category": evidence_by_category,
            "ownership_fields": ownership_fields,
            "source_status": source_status,
            "t": i18n.get_translator(lang),
            "lang": lang,
            "severity_colors": {
                "critical": "danger",
                "high": "danger",
                "medium": "warning",
                "low": "info",
            },
            "level_colors": {
                "Critical": "danger",
                "High": "danger",
                "Medium": "warning",
                "Low": "success",
                "Inconclusive": "secondary",
            },
        }

    def _build_source_status(self, raw_sources: dict[str, Any]) -> list[dict[str, Any]]:
        """Build per-source status rows from all available raw sources."""
        if not isinstance(raw_sources, dict):
            return []

        source_status: list[dict[str, Any]] = []
        for source_key, payload in raw_sources.items():
            data = payload.get("data", {}) if isinstance(payload, dict) else {}
            message = None
            if isinstance(payload, dict):
                message = payload.get("error")
                if not message and isinstance(data, dict):
                    message = data.get("error")

            source_status.append(
                {
                    "name": self.SOURCE_DISPLAY_NAMES.get(
                        source_key, source_key.replace("_", " ").title()
                    ),
                    "ok": bool(payload.get("ok"))
                    if isinstance(payload, dict)
                    else False,
                    "message": message,
                }
            )

        return source_status
