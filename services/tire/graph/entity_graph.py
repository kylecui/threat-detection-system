"""
Basic entity graph for threat intelligence correlation.
"""

from typing import Dict, List, Set, Optional, Any
from dataclasses import dataclass
from enum import Enum


class NodeType(Enum):
    IP = "ip"
    DOMAIN = "domain"
    ASN = "asn"
    HOSTNAME = "hostname"
    EVENT = "event"
    UNKNOWN = "unknown"


class EdgeType(Enum):
    RESOLVES_TO = "resolves_to"
    BELONGS_TO = "belongs_to"
    CONNECTED_TO = "connected_to"
    RELATED_TO = "related_to"


@dataclass
class Node:
    """Graph node representing an entity."""

    id: str
    type: NodeType
    properties: Dict[str, Any] = None

    def __post_init__(self):
        if self.properties is None:
            self.properties = {}


@dataclass
class Edge:
    """Graph edge representing relationship between entities."""

    source: str
    target: str
    type: EdgeType
    properties: Dict[str, Any] = None

    def __post_init__(self):
        if self.properties is None:
            self.properties = {}


class EntityGraph:
    """In-memory graph for entity correlation."""

    def __init__(self):
        self.nodes: Dict[str, Node] = {}
        self.edges: List[Edge] = {}
        self.adjacency: Dict[str, Set[str]] = {}  # node_id -> set of connected node_ids

    def add_node(self, node: Node) -> None:
        """Add a node to the graph."""
        self.nodes[node.id] = node
        if node.id not in self.adjacency:
            self.adjacency[node.id] = set()

    def add_edge(self, edge: Edge) -> None:
        """Add an edge to the graph."""
        # Ensure nodes exist
        if edge.source not in self.nodes:
            self.nodes[edge.source] = Node(edge.source, NodeType.UNKNOWN)
        if edge.target not in self.nodes:
            self.nodes[edge.target] = Node(edge.target, NodeType.UNKNOWN)

        self.edges.append(edge)
        self.adjacency[edge.source].add(edge.target)
        self.adjacency[edge.target].add(edge.source)

    def get_neighbors(self, node_id: str) -> List[str]:
        """Get neighboring node IDs."""
        return list(self.adjacency.get(node_id, set()))

    def get_node(self, node_id: str) -> Optional[Node]:
        """Get node by ID."""
        return self.nodes.get(node_id)

    def find_path(self, start: str, end: str) -> Optional[List[str]]:
        """Find shortest path between two nodes (BFS)."""
        if start not in self.nodes or end not in self.nodes:
            return None

        visited = set()
        queue = [(start, [start])]

        while queue:
            current, path = queue.pop(0)
            if current == end:
                return path

            if current not in visited:
                visited.add(current)
                for neighbor in self.adjacency.get(current, set()):
                    if neighbor not in visited:
                        queue.append((neighbor, path + [neighbor]))

        return None

    def get_related_entities(
        self, node_id: str, max_depth: int = 2
    ) -> Dict[str, List[str]]:
        """Get related entities within depth."""
        related = {}
        visited = set()

        def dfs(current: str, depth: int):
            if depth > max_depth or current in visited:
                return
            visited.add(current)

            node = self.nodes.get(current)
            if node:
                node_type = node.type.value
                if node_type not in related:
                    related[node_type] = []
                if current != node_id:
                    related[node_type].append(current)

            for neighbor in self.adjacency.get(current, set()):
                dfs(neighbor, depth + 1)

        dfs(node_id, 0)
        return related
