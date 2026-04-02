"""
Noise engine for identifying background internet noise and benign scanning activity.
"""

import logging
from typing import List, Tuple, Dict, Any
from models import IPProfile, EvidenceItem

logger = logging.getLogger(__name__)


class NoiseEngine:
    """Analyzes IP for noise characteristics."""

    # Known noise signatures
    NOISE_SIGNATURES = {
        "measurement_traffic": [
            "censys",
            "shodan",
            "masscan",
            "zmap",
            "cariddi",
            "nuclei",
        ],
        "common_scanners": [
            "nmap",
            "nessus",
            "openvas",
            "qualys",
            "rapid7",
            "metasploit",
        ],
        "benign_scanners": ["google", "bing", "yahoo", "baidu", "yandex", "apple"],
    }

    def analyze(self, profile: IPProfile) -> Tuple[float, str, List[EvidenceItem]]:
        """
        Analyze IP for noise characteristics.

        Returns:
            Tuple of (noise_score, classification, evidence_list)
        """
        score = 0.0
        classification = "unknown"
        evidence = []

        sources = profile.sources or {}

        # Check GreyNoise classification
        if "greynoise" in sources and sources["greynoise"]["ok"]:
            gn_data = sources["greynoise"]["data"]
            gn_classification = gn_data.get("classification", "").lower()

            if gn_classification in ["noise", "riot"]:
                score += 0.8
                classification = "internet_noise"
                evidence.append(
                    EvidenceItem(
                        source="greynoise",
                        category="noise",
                        severity="low",
                        title="GreyNoise noise classification",
                        detail=f"IP classified as {gn_classification} by GreyNoise",
                        score_delta=0,
                        confidence=0.9,
                    )
                )

        # Check service tags for scanner infrastructure
        scanner_tags = ["internet_scanner", "measurement_infrastructure"]
        if any(tag in profile.tags for tag in scanner_tags):
            score += 0.7
            classification = "measurement_traffic"
            evidence.append(
                EvidenceItem(
                    source="semantic",
                    category="noise",
                    severity="low",
                    title="Scanner infrastructure detected",
                    detail=f"IP tagged as {', '.join(scanner_tags)}",
                    score_delta=0,
                    confidence=0.8,
                )
            )

        # Check Shodan for scanner signatures
        if "shodan" in sources and sources["shodan"]["ok"]:
            shodan_data = sources["shodan"]["data"]
            services = shodan_data.get("services", [])

            for service in services:
                banner = service.get("banner", "").lower()
                product = service.get("product", "").lower()

                # Check for known scanner signatures
                for noise_type, signatures in self.NOISE_SIGNATURES.items():
                    if any(sig in banner or sig in product for sig in signatures):
                        score += 0.6
                        classification = noise_type
                        evidence.append(
                            EvidenceItem(
                                source="shodan",
                                category="noise",
                                severity="low",
                                title=f"Scanner signature detected: {noise_type}",
                                detail=f"Service banner contains {signatures[0]} signature",
                                score_delta=0,
                                confidence=0.7,
                            )
                        )
                        break

        # Check for port scanning patterns (many open ports)
        if "shodan" in sources and sources["shodan"]["ok"]:
            ports = shodan_data.get("ports", [])
            if len(ports) > 20:  # Arbitrary threshold for port scanning
                score += 0.5
                if classification == "unknown":
                    classification = "common_scanner"
                evidence.append(
                    EvidenceItem(
                        source="shodan",
                        category="noise",
                        severity="low",
                        title="High port count detected",
                        detail=f"IP has {len(ports)} open ports, suggesting scanning activity",
                        score_delta=0,
                        confidence=0.6,
                    )
                )

        # Determine final classification based on score
        if score >= 0.8:
            classification = "internet_noise"
        elif score >= 0.6:
            if classification == "unknown":
                classification = "measurement_traffic"
        elif score >= 0.4:
            if classification == "unknown":
                classification = "common_scanner"
        else:
            classification = "not_noisy"

        return score, classification, evidence
