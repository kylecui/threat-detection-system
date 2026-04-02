"""
Authentication utilities for the admin portal.

Uses Starlette SessionMiddleware for cookie-based sessions.
"""

import logging
from typing import Any, Optional

from fastapi import Request, HTTPException
from starlette.responses import RedirectResponse

from admin.database import admin_db

logger = logging.getLogger(__name__)


def get_current_user(request: Request) -> Optional[dict[str, Any]]:
    """Get current user from session. Returns None if not logged in."""
    user_id = request.session.get("user_id")
    if not user_id:
        return None
    user = admin_db.get_user_by_id(user_id)
    if not user or not user.get("is_active"):
        request.session.clear()
        return None
    return user


def require_login(request: Request) -> dict[str, Any]:
    """Get current user or raise 401. Use as inline check in routes."""
    user = get_current_user(request)
    if not user:
        raise HTTPException(status_code=401, detail="Not authenticated")
    return user


def require_admin(request: Request) -> dict[str, Any]:
    """Get current admin user or raise 403."""
    user = require_login(request)
    if not user.get("is_admin"):
        raise HTTPException(status_code=403, detail="Admin access required")
    return user


def login_redirect(request: Request) -> RedirectResponse:
    """Redirect to login page, preserving root_path and original URL as 'next'."""
    from app.config import settings
    from urllib.parse import quote

    # Capture the original URL so we can redirect back after login
    original_url = str(request.url.path)
    root_path = settings.root_path.rstrip("/")
    if (
        root_path
        and original_url.startswith("/")
        and not original_url.startswith(root_path)
    ):
        original_url = f"{root_path}{original_url}"
    query = str(request.url.query)
    if query:
        original_url = f"{original_url}?{query}"

    next_param = quote(original_url, safe="")
    return RedirectResponse(
        url=f"{settings.root_path}/admin/login?next={next_param}",
        status_code=303,
    )
