"""
Correlator for establishing relationships between entities.
"""

from typing import List, Dict, Any, Optional
from models import IPProfile
from .entity_graph import EntityGraph, Node, NodeType, Edge, EdgeType


class Correlator:
    """Establishes correlations between threat intelligence entities."""

    def __init__(self):
        self.graph = EntityGraph()

    def correlate_from_profile(self, profile: IPProfile) -> Dict[str, Any]:
        """Build correlations from IP profile and return summary."""
        ip = profile.ip

        # Add IP node
        self.graph.add_node(Node(ip, NodeType.IP, {"profile": profile.dict()}))

        # Correlate with sources
        self._correlate_sources(ip, profile.sources)

        # Get related entities summary
        related = self.graph.get_related_entities(ip, max_depth=2)

        return {
            "entity_count": len(self.graph.nodes),
            "relationship_count": len(self.graph.edges),
            "related_entities": related,
            "correlation_summary": self._generate_summary(related),
        }

    def _correlate_sources(self, ip: str, sources: Dict[str, Any]) -> None:
        """Correlate entities from various sources."""
        if not sources:
            return

        # RDAP correlations
        if "rdap" in sources and sources["rdap"]["ok"]:
            rdap_data = sources["rdap"]["data"]
            asn = rdap_data.get("asn")
            if asn:
                self._add_asn_relationship(ip, str(asn))

        # Reverse DNS correlations
        if "reverse_dns" in sources and sources["reverse_dns"]["ok"]:
            rdns_data = sources["reverse_dns"]["data"]
            hostnames = rdns_data.get("hostnames", [])
            for hostname in hostnames[:5]:  # Limit
                self._add_hostname_relationship(ip, hostname)

        # Shodan correlations
        if "shodan" in sources and sources["shodan"]["ok"]:
            shodan_data = sources["shodan"]["data"]
            hostnames = shodan_data.get("hostnames", [])
            domains = shodan_data.get("domains", [])
            for hostname in hostnames[:3]:
                self._add_hostname_relationship(ip, hostname)
            for domain in domains[:3]:
                self._add_domain_relationship(ip, domain)

        # VirusTotal correlations
        if "virustotal" in sources and sources["virustotal"]["ok"]:
            vt_data = sources["virustotal"]["data"]
            related_domains = vt_data.get("related_domains", [])
            for domain in related_domains[:3]:
                self._add_domain_relationship(ip, domain)

    def _add_asn_relationship(self, ip: str, asn: str) -> None:
        """Add IP -> ASN relationship."""
        self.graph.add_node(Node(asn, NodeType.ASN))
        self.graph.add_edge(Edge(ip, asn, EdgeType.BELONGS_TO))

    def _add_hostname_relationship(self, ip: str, hostname: str) -> None:
        """Add IP -> Hostname relationship."""
        self.graph.add_node(Node(hostname, NodeType.HOSTNAME))
        self.graph.add_edge(Edge(ip, hostname, EdgeType.RESOLVES_TO))

    def _add_domain_relationship(self, ip: str, domain: str) -> None:
        """Add IP -> Domain relationship."""
        self.graph.add_node(Node(domain, NodeType.DOMAIN))
        self.graph.add_edge(Edge(ip, domain, EdgeType.RESOLVES_TO))

    def _generate_summary(self, related: Dict[str, List[str]]) -> str:
        """Generate human-readable correlation summary."""
        parts = []
        for entity_type, entities in related.items():
            if entities:
                count = len(entities)
                parts.append(f"{count} {entity_type}{'s' if count > 1 else ''}")

        if parts:
            return f"Correlated with {', '.join(parts)}"
        else:
            return "No significant correlations found"
