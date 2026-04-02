"""
Main service interface for threat intelligence analysis.
"""

from typing import Optional
from models import Observable, ContextProfile, Verdict
from app.query_engine import QueryEngine


class ThreatIntelService:
    """Main service class for threat intelligence analysis."""

    def __init__(self):
        self.query_engine = QueryEngine()

    async def analyze_ip(
        self,
        ip: str,
        context: Optional[ContextProfile] = None,
        refresh: bool = False,
        user_id: Optional[int] = None,
    ) -> Verdict:
        """Analyze an IP address for threats.

        Args:
            ip: IP address to analyze.
            context: Optional contextual profile for enhanced analysis.
            refresh: If True, bypass cache and re-query all sources.
            user_id: Authenticated user ID for per-user API key resolution.
        """
        observable = Observable(type="ip", value=ip)
        return await self.query_engine.analyze(
            observable, context, refresh, user_id=user_id
        )

    async def analyze_domain(
        self,
        domain: str,
        context: Optional[ContextProfile] = None,
        refresh: bool = False,
        user_id: Optional[int] = None,
    ) -> Verdict:
        """Analyze a domain for threats."""
        observable = Observable(type="domain", value=domain)
        return await self.query_engine.analyze(
            observable, context, refresh, user_id=user_id
        )

    async def analyze_url(
        self,
        url: str,
        context: Optional[ContextProfile] = None,
        refresh: bool = False,
        user_id: Optional[int] = None,
    ) -> Verdict:
        """Analyze a URL for threats."""
        observable = Observable(type="url", value=url)
        return await self.query_engine.analyze(
            observable, context, refresh, user_id=user_id
        )
