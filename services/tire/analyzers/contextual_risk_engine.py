"""
Contextual risk engine for analyzing behavioral context.
"""

import logging
from typing import List, Tuple, Optional
from models import IPProfile, ContextProfile, EvidenceItem

logger = logging.getLogger(__name__)


class ContextualRiskEngine:
    """Analyzes risk based on behavioral context."""

    def analyze(
        self, profile: IPProfile, context: Optional[ContextProfile]
    ) -> Tuple[int, List[EvidenceItem]]:
        """
        Analyze contextual risk.

        Args:
            profile: IP profile
            context: Behavioral context

        Returns:
            Tuple of (contextual_score_adjustment, evidence_list)
        """
        if not context:
            return 0, []

        score_adjustment = 0
        evidence = []

        # Rule 1: Outbound HTTPS to Microsoft service with Office hostname -> likely legitimate
        if (
            context.direction == "outbound"
            and context.port == 443
            and "microsoft_service" in profile.tags
            and context.hostname
            and (
                "office" in context.hostname.lower()
                or "ecs" in context.hostname.lower()
            )
        ):
            score_adjustment -= 20
            evidence.append(
                EvidenceItem(
                    source="context",
                    category="legitimacy",
                    severity="low",
                    title="Legitimate Microsoft Office traffic",
                    detail=f"Outbound HTTPS to {context.hostname} on Microsoft infrastructure",
                    score_delta=-20,
                    confidence=0.9,
                )
            )

        # Rule 2: Outbound from known legitimate processes -> reduce suspicion
        legitimate_processes = [
            "browser",
            "office",
            "defender",
            "outlook",
            "teams",
            "edge",
            "chrome",
            "firefox",
        ]
        if (
            context.direction == "outbound"
            and context.process_name
            and any(
                proc in context.process_name.lower() for proc in legitimate_processes
            )
        ):
            score_adjustment -= 15
            evidence.append(
                EvidenceItem(
                    source="context",
                    category="legitimacy",
                    severity="low",
                    title="Legitimate process outbound traffic",
                    detail=f"Traffic from legitimate process: {context.process_name}",
                    score_delta=-15,
                    confidence=0.8,
                )
            )

        # Rule 3: Inbound traffic to high-risk ports with scanning patterns -> increase risk
        high_risk_ports = [445, 3389, 22, 23, 3389, 5900]  # SMB, RDP, SSH, Telnet, VNC
        if context.direction == "inbound" and context.port in high_risk_ports:
            score_adjustment += 25
            evidence.append(
                EvidenceItem(
                    source="context",
                    category="threat",
                    severity="high",
                    title="High-risk inbound traffic",
                    detail=f"Inbound traffic to port {context.port} ({self._port_name(context.port)})",
                    score_delta=25,
                    confidence=0.85,
                )
            )

        # Rule 4: Suspicious download processes -> increase risk
        suspicious_processes = ["powershell", "curl", "wget", "bitsadmin", "certutil"]
        if context.process_name and any(
            proc in context.process_name.lower() for proc in suspicious_processes
        ):
            score_adjustment += 25
            evidence.append(
                EvidenceItem(
                    source="context",
                    category="threat",
                    severity="high",
                    title="Suspicious download process",
                    detail=f"Traffic associated with suspicious process: {context.process_name}",
                    score_delta=25,
                    confidence=0.8,
                )
            )

        # Rule 5: Single short-lived connections with no payload -> likely benign
        # This would need more context from flow data, but for now assume it's benign
        if context.direction == "outbound" and context.port in [80, 443]:
            score_adjustment -= 10
            evidence.append(
                EvidenceItem(
                    source="context",
                    category="benign",
                    severity="low",
                    title="Likely benign web traffic",
                    detail=f"Outbound web traffic to port {context.port}",
                    score_delta=-10,
                    confidence=0.6,
                )
            )

        return score_adjustment, evidence

    def _port_name(self, port: int) -> str:
        """Get service name for common ports."""
        port_names = {
            22: "SSH",
            23: "Telnet",
            80: "HTTP",
            443: "HTTPS",
            445: "SMB",
            3389: "RDP",
            5900: "VNC",
        }
        return port_names.get(port, "Unknown")
