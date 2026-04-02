"""
Narrative reporter for detailed threat intelligence reports.

Generates a comprehensive, analyst-quality report using a hybrid approach:
- Template layer (Jinja2): structure, tables, score cards, reference links
- LLM layer (optional): analytical narrative paragraphs

Falls back to template-only mode when LLM is unavailable.
"""

import logging
import os
from datetime import UTC, datetime
from typing import Any, Dict, List, Optional, Tuple

from jinja2 import Environment, FileSystemLoader

from app.config import settings
from app.i18n import i18n
from app.llm_client import llm_client
from models import Verdict

logger = logging.getLogger(__name__)


class NarrativeReporter:
    """Generates detailed narrative threat intelligence reports."""

    SECTION_MARKERS = [
        "===EXECUTIVE_SUMMARY===",
        "===CROSS_REFERENCE===",
        "===NOISE_ANALYSIS===",
        "===RISK_ASSESSMENT===",
        "===RECOMMENDATIONS===",
        "===CONCLUSION===",
    ]

    def __init__(self):
        template_dir = os.path.join(os.path.dirname(__file__), "..", "templates")
        self.env = Environment(loader=FileSystemLoader(template_dir))

    async def generate(
        self,
        verdict: Verdict,
        lang: str = "en",
        llm_overrides: Optional[Dict[str, Any]] = None,
        query_date: Optional[datetime] = None,
    ) -> tuple[str, bool, bool]:
        """
        Generate a detailed narrative report.

        Args:
            verdict: Analysis verdict with raw_sources
            lang: Language code ("en" or "zh")
            llm_overrides: Per-user LLM settings dict with optional
                           api_key, model, base_url keys
            query_date: When the underlying data was queried. If provided,
                        the report includes a staleness warning when the
                        data is older than the configured threshold.

        Returns:
            Complete HTML page string
        """
        source_data = self._extract_source_data(verdict)
        facts = self._build_structured_facts(source_data, verdict)

        # Try LLM generation (with per-user overrides when available)
        llm_sections: Dict[str, str] = {}
        llm_enhanced = False
        overrides = llm_overrides or {}
        llm_requested = bool(
            overrides
            and (overrides.get("api_key") or overrides.get("source") != "template")
        )
        if llm_client.is_enabled(overrides):
            logger.info(
                "Attempting LLM enhancement for report (ip=%s, lang=%s, source=%s, model=%s)",
                verdict.object_value,
                lang,
                overrides.get("source", "default"),
                overrides.get("model") or llm_client.model,
            )
            system_prompt, user_prompt = self._build_llm_prompt(facts, lang)
            response = await llm_client.generate(
                system_prompt,
                user_prompt,
                api_key=overrides.get("api_key") or None,
                model=overrides.get("model") or None,
                base_url=overrides.get("base_url") or None,
                user_id=overrides.get("user_id"),
                source=overrides.get("source", "template"),
                fingerprint=overrides.get("fingerprint", ""),
                shared_config_id=overrides.get("shared_config_id"),
            )
            if response:
                llm_sections = self._parse_llm_response(response)
                llm_enhanced = bool(llm_sections)
                if llm_enhanced:
                    logger.info(
                        "Report rendered with LLM-enhanced narrative (ip=%s, lang=%s)",
                        verdict.object_value,
                        lang,
                    )
                else:
                    logger.warning(
                        "LLM returned empty/invalid narrative sections; using fallback template content (ip=%s, lang=%s)",
                        verdict.object_value,
                        lang,
                    )
            else:
                logger.warning(
                    "LLM enhancement unavailable; using fallback template content (ip=%s, lang=%s, source=%s, model=%s)",
                    verdict.object_value,
                    lang,
                    overrides.get("source", "default"),
                    overrides.get("model") or llm_client.model,
                )
        else:
            logger.info(
                "LLM disabled for report; using template mode (ip=%s, lang=%s, source=%s)",
                verdict.object_value,
                lang,
                overrides.get("source", "default"),
            )

        # Always generate fallback sections
        fallback_sections = self._generate_fallback_sections(source_data, verdict, lang)

        template = self.env.get_template("narrative_report.html.j2")
        t = i18n.get_translator(lang)

        # Timestamp handling
        now = datetime.now(UTC)
        report_generated_at = now.strftime("%Y-%m-%d %H:%M:%S")
        query_date_str = (
            query_date.strftime("%Y-%m-%d %H:%M:%S") if query_date else None
        )

        # Staleness calculation
        staleness_days = settings.result_staleness_days
        is_stale = False
        days_old = 0
        if query_date:
            delta = now - query_date
            days_old = delta.days
            is_stale = days_old >= staleness_days

        return (
            template.render(
                verdict=verdict,
                source_data=source_data,
                llm_sections=llm_sections,
                fallback_sections=fallback_sections,
                llm_enhanced=llm_enhanced,
                llm_fallback=llm_requested and not llm_enhanced,
                t=t,
                lang=lang,
                timestamp=report_generated_at,
                query_date=query_date_str,
                report_generated_at=report_generated_at,
                is_stale=is_stale,
                staleness_days=staleness_days,
                days_old=days_old,
            ),
            llm_enhanced,
            llm_requested and not llm_enhanced,
        )

    # ------------------------------------------------------------------
    # Data extraction
    # ------------------------------------------------------------------

    def _extract_source_data(self, verdict: Verdict) -> Dict[str, Any]:
        """Extract and organize raw_sources into a clean structure."""
        rs = verdict.raw_sources or {}

        def _get(source: str, key: str, default: Any = None) -> Any:
            entry = rs.get(source, {})
            if not isinstance(entry, dict) or not entry.get("ok"):
                return default
            data = entry.get("data")
            if not isinstance(data, dict):
                return default
            return data.get(key, default)

        def _get_all(source: str) -> Optional[Dict[str, Any]]:
            entry = rs.get(source, {})
            if not isinstance(entry, dict) or not entry.get("ok"):
                return None
            data = entry.get("data")
            return data if isinstance(data, dict) else None

        # RDAP / ownership
        rdap = _get_all("rdap")
        rdns = _get_all("reverse_dns")
        abuseipdb = _get_all("abuseipdb")
        threatbook = _get_all("threatbook")

        # Build domain correlation from multiple sources
        rdns_hostname = _get("reverse_dns", "hostname")
        rdns_aliases = _get("reverse_dns", "aliases", [])
        vt_data = _get_all("virustotal")
        vt_domains = (vt_data or {}).get("related_domains", [])

        organization = self._first_non_empty(
            _get("rdap", "name"),
            (abuseipdb or {}).get("isp"),
            (threatbook or {}).get("carrier"),
            (vt_data or {}).get("as_owner"),
        )
        country = self._first_non_empty(
            _get("rdap", "country"),
            (threatbook or {}).get("country"),
            (abuseipdb or {}).get("country_code"),
        )
        network = _get("rdap", "network")
        ownership_available = any(
            [
                _get("rdap", "asn"),
                organization,
                country,
                network,
                rdns_hostname,
                bool(rdns_aliases),
            ]
        )

        return {
            "ip": verdict.object_value,
            # Ownership
            "asn": _get("rdap", "asn"),
            "organization": organization,
            "country": country,
            "network": network,
            "handle": _get("rdap", "handle"),
            "rdap_type": _get("rdap", "type"),
            "rdap_entities": self._format_rdap_entities(_get("rdap", "entities", [])),
            "ownership_available": ownership_available,
            # Reverse DNS
            "rdns_hostname": rdns_hostname,
            "rdns_aliases": rdns_aliases,
            # Domain correlation (merged from rDNS + VT resolutions)
            "rdns_domains": self._collect_rdns_domains(rdns_hostname, rdns_aliases),
            "vt_resolutions": vt_domains,
            # AbuseIPDB
            "abuseipdb": abuseipdb,
            # VirusTotal
            "virustotal": vt_data,
            # OTX
            "otx": _get_all("otx"),
            # GreyNoise
            "greynoise": _get_all("greynoise"),
            # Shodan
            "shodan": _get_all("shodan"),
            # ThreatBook
            "threatbook": threatbook,
            # Internal
            "honeynet": _get_all("honeynet"),
            "internal_flow": _get_all("internal_flow"),
            # Collector availability
            "available_sources": [
                s for s in rs if isinstance(rs.get(s), dict) and rs[s].get("ok")
            ],
        }

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------

    @staticmethod
    def _first_non_empty(*values: Any) -> Any:
        """Return the first non-empty scalar value."""
        for value in values:
            if value is None:
                continue
            if isinstance(value, str) and not value.strip():
                continue
            if isinstance(value, (list, dict)) and not value:
                continue
            return value
        return None

    @staticmethod
    def _format_rdap_entities(entities: Any) -> List[str]:
        """Convert raw RDAP entity dicts into human-readable strings.

        Each entity is ``{"handle": "...", "roles": [...], "vcard": [...]}``.
        We extract the ``fn`` (full-name) from the vcard and combine it with
        roles to produce e.g. ``"Censys, Inc. (registrant)"``.
        """
        if not isinstance(entities, list):
            return []

        results: List[str] = []
        for ent in entities:
            if not isinstance(ent, dict):
                continue

            name = ent.get("handle", "")

            # Try to extract human-readable name from vcard
            vcard = ent.get("vcard")
            if isinstance(vcard, list) and len(vcard) >= 2:
                # vcard structure: ["vcard", [ [field, {}, type, value], ... ]]
                fields = vcard[1] if isinstance(vcard[1], list) else []
                for field in fields:
                    if isinstance(field, list) and len(field) >= 4 and field[0] == "fn":
                        name = str(field[3])
                        break

            roles = ent.get("roles", [])
            role_str = ", ".join(roles) if isinstance(roles, list) else ""

            if name and role_str:
                results.append(f"{name} ({role_str})")
            elif name:
                results.append(name)

        return results

    @staticmethod
    def _collect_rdns_domains(
        hostname: Optional[str], aliases: Optional[list]
    ) -> List[str]:
        """Collect unique domain names from reverse DNS hostname and aliases."""
        domains: List[str] = []
        if hostname:
            domains.append(hostname)
        if isinstance(aliases, list):
            for alias in aliases:
                if alias and alias not in domains:
                    domains.append(alias)
        return domains

    # ------------------------------------------------------------------
    # Structured facts (for LLM prompt)
    # ------------------------------------------------------------------

    def _build_structured_facts(self, data: Dict[str, Any], verdict: Verdict) -> str:
        """Convert extracted data to natural language sentences for LLM."""
        lines: List[str] = []
        ip = data["ip"]

        # Ownership facts
        if data.get("asn") or data.get("organization"):
            org = data.get("organization", "Unknown")
            asn = data.get("asn", "Unknown")
            country = data.get("country", "Unknown")
            lines.append(
                f"The IP {ip} belongs to {asn} (organization: {org}), located in {country}."
            )
        if data.get("network"):
            lines.append(f"Network CIDR: {data['network']}.")
        if data.get("rdns_hostname"):
            lines.append(f"Reverse DNS resolves to: {data['rdns_hostname']}.")
        if data.get("rdns_aliases"):
            lines.append(f"DNS aliases: {', '.join(data['rdns_aliases'])}.")

        # AbuseIPDB
        abuse = data.get("abuseipdb")
        if abuse:
            score = abuse.get("abuse_confidence_score", "N/A")
            total = abuse.get("total_reports", 0)
            isp = abuse.get("isp", "")
            usage = abuse.get("usage_type", "")
            lines.append(
                f"AbuseIPDB reports abuse confidence score {score}/100 with {total} total reports."
            )
            if isp:
                lines.append(f"AbuseIPDB ISP: {isp}.")
            if usage:
                lines.append(f"AbuseIPDB usage type: {usage}.")
            if abuse.get("is_whitelisted"):
                lines.append("AbuseIPDB has this IP whitelisted.")

        # VirusTotal
        vt = data.get("virustotal")
        if vt:
            mal = vt.get("malicious_count", 0)
            sus = vt.get("suspicious_count", 0)
            harm = vt.get("harmless_count", 0)
            undet = vt.get("undetected_count", 0)
            total_engines = mal + sus + harm + undet
            lines.append(
                f"VirusTotal: {mal}/{total_engines} vendors flagged as malicious, "
                f"{sus} suspicious, {harm} harmless, {undet} undetected."
            )
            if vt.get("as_owner"):
                lines.append(f"VirusTotal AS owner: {vt['as_owner']}.")
            if vt.get("related_domains"):
                domains = vt["related_domains"]
                if isinstance(domains, list):
                    lines.append(
                        f"VirusTotal passive DNS resolutions: {', '.join(domains[:10])}."
                    )

        # OTX
        otx = data.get("otx")
        if otx:
            pulses = otx.get("pulse_count", 0)
            rep = otx.get("reputation", 0)
            lines.append(
                f"AlienVault OTX: {pulses} threat pulses reference this IP, reputation score {rep}."
            )

        # GreyNoise
        gn = data.get("greynoise")
        if gn:
            classification = gn.get("classification", "unknown")
            noise = gn.get("noise", False)
            riot = gn.get("riot", False)
            lines.append(
                f"GreyNoise classification: {classification}, "
                f"noise={'yes' if noise else 'no'}, RIOT={'yes' if riot else 'no'}."
            )

        # Shodan
        shodan = data.get("shodan")
        if shodan:
            ports = shodan.get("ports", [])
            vulns = shodan.get("vulns_count", 0)
            lines.append(f"Shodan open ports: {ports}.")
            if vulns:
                lines.append(f"Shodan known vulnerabilities: {vulns}.")
            services = shodan.get("services", [])
            if services:
                svc_summary = [
                    f"{s.get('port')}/{s.get('transport', '?')} ({s.get('product', 'unknown')})"
                    for s in services[:5]
                ]
                lines.append(f"Shodan services: {', '.join(svc_summary)}.")

        # Honeynet / Internal
        honeynet = data.get("honeynet")
        if honeynet and honeynet.get("total_hits", 0) > 0:
            lines.append(f"Internal honeynet: {honeynet['total_hits']} hits detected.")
        internal = data.get("internal_flow")
        if internal and internal.get("session_count", 0) > 0:
            lines.append(
                f"Internal flow: {internal['session_count']} sessions observed."
            )

        # Verdict facts
        lines.append(
            f"TIRE verdict: {verdict.level} (score {verdict.final_score}/100)."
        )
        lines.append(
            f"Reputation score: {verdict.reputation_score}, contextual score: {verdict.contextual_score}."
        )
        lines.append(f"Confidence: {verdict.confidence:.1%}.")
        lines.append(f"Decision: {verdict.decision}.")
        if verdict.tags:
            lines.append(f"Semantic tags: {', '.join(verdict.tags)}.")

        # Evidence summary
        for e in verdict.evidence:
            lines.append(
                f"Evidence [{e.source}]: {e.title} — {e.detail} "
                f"(severity: {e.severity}, score_delta: {e.score_delta:+d})."
            )

        return "\n".join(lines)

    # ------------------------------------------------------------------
    # LLM prompt construction
    # ------------------------------------------------------------------

    def _build_llm_prompt(self, facts: str, lang: str) -> Tuple[str, str]:
        """Build system and user prompts for LLM report generation."""
        lang_instruction = (
            "Write the report in Chinese (简体中文)."
            if lang == "zh"
            else "Write the report in English."
        )

        system_prompt = (
            "You are a senior SOC analyst writing a formal threat intelligence report. "
            "Your analysis must be precise, evidence-based, and actionable. "
            "Never fabricate data — only reference facts provided below. "
            "If data is insufficient for a conclusion, state that clearly."
        )

        user_prompt = f"""Based on the following structured intelligence facts, generate analytical narrative paragraphs for a detailed threat report.

{lang_instruction}

## Structured Facts
{facts}

## Output Format
Generate exactly 6 sections, each preceded by its marker on a separate line. Write 2-5 sentences per section.

===EXECUTIVE_SUMMARY===
Provide a concise executive summary: what was analyzed, key findings, and overall risk determination.

===CROSS_REFERENCE===
Cross-reference findings across different intelligence sources. Identify corroborations or contradictions between sources.

===NOISE_ANALYSIS===
Analyze potential noise and false positives. Consider whether community reports may be automated scanners, shared infrastructure effects, or legitimate services being misreported.

===RISK_ASSESSMENT===
Provide a risk assessment narrative explaining the score breakdown, what factors contribute most to the risk level, and confidence in the assessment.

===RECOMMENDATIONS===
Provide specific, actionable handling recommendations based on the risk level and context. Include monitoring, blocking, or investigation suggestions as appropriate.

===CONCLUSION===
Provide a final conclusion with recommended classification tags and next steps."""

        return system_prompt, user_prompt

    # ------------------------------------------------------------------
    # LLM response parsing
    # ------------------------------------------------------------------

    def _parse_llm_response(self, response: str) -> Dict[str, str]:
        """Parse LLM response by section markers."""
        sections: Dict[str, str] = {}
        marker_map = {
            "===EXECUTIVE_SUMMARY===": "executive_summary",
            "===CROSS_REFERENCE===": "cross_reference",
            "===NOISE_ANALYSIS===": "noise_analysis",
            "===RISK_ASSESSMENT===": "risk_assessment",
            "===RECOMMENDATIONS===": "recommendations",
            "===CONCLUSION===": "conclusion",
        }

        current_key: Optional[str] = None
        current_lines: List[str] = []

        for line in response.split("\n"):
            stripped = line.strip()
            if stripped in marker_map:
                # Save previous section
                if current_key and current_lines:
                    sections[current_key] = "\n".join(current_lines).strip()
                current_key = marker_map[stripped]
                current_lines = []
            elif current_key is not None:
                current_lines.append(line)

        # Save last section
        if current_key and current_lines:
            sections[current_key] = "\n".join(current_lines).strip()

        if sections:
            logger.info("Parsed %d LLM sections", len(sections))
        else:
            logger.warning("Failed to parse LLM response into sections")

        return sections

    # ------------------------------------------------------------------
    # Template-only fallback
    # ------------------------------------------------------------------

    def _generate_fallback_sections(
        self, data: Dict[str, Any], verdict: Verdict, lang: str
    ) -> Dict[str, str]:
        """Generate template-only fallback text when LLM is unavailable."""
        ip = data["ip"]
        t = i18n.get_translator(lang)

        # Executive summary
        exec_summary = (
            f"{verdict.object_type.upper()} {ip} — "
            f"{t('level.' + verdict.level)} ({verdict.final_score}/100). "
            f"{verdict.summary}"
        )

        # Cross reference
        sources_used = data.get("available_sources", [])
        cross_ref = f"{len(sources_used)} intelligence sources queried: {', '.join(sources_used)}. "
        # Add corroboration note
        positive_sources = [e.source for e in verdict.evidence if e.score_delta > 0]
        negative_sources = [e.source for e in verdict.evidence if e.score_delta < 0]
        if positive_sources:
            cross_ref += f"Threat signals from: {', '.join(set(positive_sources))}. "
        if negative_sources:
            cross_ref += (
                f"Mitigating signals from: {', '.join(set(negative_sources))}. "
            )

        # Noise analysis
        noise_text = ""
        abuse = data.get("abuseipdb")
        if abuse:
            score = abuse.get("abuse_confidence_score", 0)
            total = abuse.get("total_reports", 0)
            if total > 0 and score < 30:
                noise_text = (
                    f"AbuseIPDB reports {total} community reports with low confidence ({score}/100). "
                    "This may indicate automated scanning or shared infrastructure noise."
                )
            elif total > 0:
                noise_text = f"AbuseIPDB reports {total} community reports with confidence {score}/100."
        if (
            "internet_scanner" in verdict.tags
            or "measurement_infrastructure" in verdict.tags
        ):
            noise_text += " Semantic analysis identifies this as known scanning/measurement infrastructure."
        if not noise_text:
            noise_text = "No significant noise indicators detected."

        # Risk assessment
        risk_text = (
            f"Final risk score: {verdict.final_score}/100 "
            f"(reputation: {verdict.reputation_score}, contextual: {verdict.contextual_score}). "
            f"Verdict level: {t('level.' + verdict.level)}. "
            f"Confidence: {verdict.confidence:.1%}."
        )

        # Recommendations
        rec_text = f"{t('decision.' + verdict.decision)}."
        if verdict.level in ("High", "Critical"):
            rec_text += f" Immediate review recommended for {ip}."
        elif verdict.level == "Medium":
            rec_text += (
                f" Further investigation recommended before allowing traffic from {ip}."
            )
        else:
            rec_text += f" Standard monitoring sufficient for {ip}."

        # Conclusion
        conclusion = (
            f"Based on analysis of {len(sources_used)} intelligence sources, "
            f"{ip} is assessed as {t('level.' + verdict.level)} risk "
            f"(score {verdict.final_score}/100). "
            f"Recommended action: {t('decision.' + verdict.decision)}."
        )
        if verdict.tags:
            conclusion += f" Tags: {', '.join(verdict.tags)}."

        return {
            "executive_summary": exec_summary,
            "cross_reference": cross_ref,
            "noise_analysis": noise_text,
            "risk_assessment": risk_text,
            "recommendations": rec_text,
            "conclusion": conclusion,
        }
