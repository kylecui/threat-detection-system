"""
Admin portal routes — dashboard, plugin management, LLM config, user profile.
"""

import ast
import json
import logging
import os
import shutil
from typing import Any

import yaml
from fastapi import APIRouter, Request, Form, UploadFile, File
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from fastapi.templating import Jinja2Templates
from starlette.responses import StreamingResponse

from admin.auth import get_current_user, login_redirect
from admin.database import admin_db
from admin.log_handler import LogStore
from app.config import settings
from app.i18n import i18n
from storage.result_store import get_result_store

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/admin")

templates = Jinja2Templates(
    directory=os.path.join(os.path.dirname(__file__), "..", "templates")
)

# ── Plugin config helpers ───────────────────────────────────────


def _plugins_yaml_path() -> str:
    return os.path.join(os.path.dirname(__file__), "..", "config", "plugins.yaml")


def _load_plugin_config() -> dict[str, Any]:
    try:
        with open(_plugins_yaml_path(), "r", encoding="utf-8") as f:
            return yaml.safe_load(f) or {}
    except Exception:
        return {}


def _save_plugin_config(config: dict[str, Any]) -> None:
    with open(_plugins_yaml_path(), "w", encoding="utf-8") as f:
        yaml.dump(
            config, f, default_flow_style=False, allow_unicode=True, sort_keys=False
        )


def _get_all_plugins_info() -> list[dict[str, Any]]:
    """Get merged info from plugins.yaml + discovered plugin metadata."""
    config = _load_plugin_config()
    plugin_configs = config.get("plugins", {})

    # Detect which plugins are community-uploaded (deletable)
    community_dir = os.path.join(
        os.path.dirname(__file__), "..", "plugins", "community"
    )
    community_names: set[str] = set()
    if os.path.isdir(community_dir):
        for f in os.listdir(community_dir):
            if f.endswith(".py") and not f.startswith("_"):
                community_names.add(f.removesuffix(".py"))

    # Try to get metadata from registry
    plugin_metadata: dict[str, Any] = {}
    try:
        from plugins import PluginRegistry

        full_config = _load_plugin_config()
        # Temporarily enable all to discover metadata
        temp_config = {"plugins": {}}
        for name, cfg in full_config.get("plugins", {}).items():
            temp_cfg = dict(cfg)
            temp_cfg["enabled"] = True
            temp_config["plugins"][name] = temp_cfg
        reg = PluginRegistry(temp_config)
        reg.discover()
        for meta in reg.list_all():
            plugin_metadata[meta.name] = {
                "display_name": meta.display_name,
                "version": meta.version,
                "description": meta.description,
                "supported_types": meta.supported_types,
                "requires_api_key": meta.requires_api_key,
                "api_key_env_var": meta.api_key_env_var,
                "tags": meta.tags,
                "priority": meta.priority,
            }
    except Exception as e:
        logger.warning(f"Could not load plugin metadata: {e}")

    shared_key_names = {
        row["plugin_name"] for row in get_result_store().list_plugin_api_keys(user_id=0)
    }
    result = []
    for name, cfg in plugin_configs.items():
        info = {
            "name": name,
            "enabled": cfg.get("enabled", True),
            "api_key_env": cfg.get("api_key_env"),
            "priority": cfg.get("priority", 50),
            "config": cfg.get("config", {}),
        }
        if name in plugin_metadata:
            info.update(plugin_metadata[name])
        else:
            info.setdefault("display_name", name.replace("_", " ").title())
            info.setdefault("version", "?")
            info.setdefault("description", "")
            info.setdefault("requires_api_key", bool(cfg.get("api_key_env")))
            info.setdefault("api_key_env_var", cfg.get("api_key_env"))
        info["api_key_configured"] = name in shared_key_names
        info["is_community"] = name in community_names
        result.append(info)
    return result


def _validate_plugin_source(source_code: str) -> dict[str, Any]:
    """Validate a plugin .py file using AST analysis.

    Checks:
      1. File is valid Python (parses without errors)
      2. Contains a class that inherits from TIPlugin
      3. Class has a 'metadata' property and 'query' method

    Returns:
        {"ok": bool, "plugin_name": str | None, "error": str | None}
    """
    try:
        tree = ast.parse(source_code)
    except SyntaxError as e:
        return {"ok": False, "plugin_name": None, "error": f"Syntax error: {e}"}

    # Find classes that inherit from TIPlugin
    plugin_classes = []
    for node in ast.walk(tree):
        if not isinstance(node, ast.ClassDef):
            continue
        base_names = []
        for base in node.bases:
            if isinstance(base, ast.Name):
                base_names.append(base.id)
            elif isinstance(base, ast.Attribute):
                base_names.append(base.attr)
        if "TIPlugin" in base_names:
            plugin_classes.append(node)

    if not plugin_classes:
        return {
            "ok": False,
            "plugin_name": None,
            "error": "No TIPlugin subclass found. Plugin must inherit from TIPlugin.",
        }

    cls = plugin_classes[0]
    method_names = set()
    has_metadata = False
    for item in cls.body:
        if isinstance(item, ast.FunctionDef) or isinstance(item, ast.AsyncFunctionDef):
            method_names.add(item.name)
            if item.name == "metadata":
                has_metadata = True
        # Check decorated properties for metadata
        if isinstance(item, ast.FunctionDef):
            for dec in item.decorator_list:
                dec_name = ""
                if isinstance(dec, ast.Name):
                    dec_name = dec.id
                elif isinstance(dec, ast.Attribute):
                    dec_name = dec.attr
                if dec_name == "property" and item.name == "metadata":
                    has_metadata = True

    if not has_metadata:
        return {
            "ok": False,
            "plugin_name": None,
            "error": "Plugin class missing 'metadata' property.",
        }
    if "query" not in method_names:
        return {
            "ok": False,
            "plugin_name": None,
            "error": "Plugin class missing 'query' method.",
        }

    # Try to infer plugin name from class or file
    plugin_name = cls.name.lower().replace("plugin", "").strip("_") or cls.name.lower()
    return {"ok": True, "plugin_name": plugin_name, "error": None}


def _get_lang(request: Request) -> str:
    """Resolve display language."""
    lang = request.query_params.get("lang")
    if lang in i18n.SUPPORTED_LANGS:
        return lang
    cookie_lang = request.cookies.get("preferred_locale")
    if cookie_lang in i18n.SUPPORTED_LANGS:
        return cookie_lang
    return settings.language


def _admin_context(request: Request, user: dict, **extra: Any) -> dict[str, Any]:
    """Build template context for admin pages."""
    lang = _get_lang(request)
    t = i18n.get_translator(lang)
    return {
        "request": request,
        "user": user,
        "t": t,
        "lang": lang,
        "root_path": settings.root_path,
        **extra,
    }


def _parse_bool(value: str | None) -> bool:
    return str(value).lower() in {"1", "true", "on", "yes"}


def _parse_int_list(values: list[str] | None) -> list[int]:
    result: list[int] = []
    for value in values or []:
        try:
            parsed = int(value)
        except (TypeError, ValueError):
            continue
        if parsed > 0:
            result.append(parsed)
    return result


def _normalize_next_url(next_url: str | None) -> str:
    """Normalize post-login redirects so mounted root paths are preserved."""
    next_url = (next_url or "").strip()
    if not next_url:
        return f"{settings.root_path}/admin/"
    if next_url.startswith(("http://", "https://", "//")):
        return f"{settings.root_path}/admin/"

    root_path = settings.root_path.rstrip("/")
    if root_path and next_url.startswith("/") and not next_url.startswith(root_path):
        return f"{root_path}{next_url}"
    return next_url


# ── Auth routes ─────────────────────────────────────────────────


@router.get("/login", response_class=HTMLResponse)
async def login_page(request: Request):
    user = get_current_user(request)
    next_url = request.query_params.get("next", "")
    if user:
        # Already logged in — honour the next param or go to admin dashboard
        redirect_to = _normalize_next_url(next_url)
        return RedirectResponse(redirect_to, status_code=303)
    lang = _get_lang(request)
    t = i18n.get_translator(lang)
    return templates.TemplateResponse(
        "admin/login.html.j2",
        {
            "request": request,
            "t": t,
            "lang": lang,
            "root_path": settings.root_path,
            "next_url": next_url,
        },
    )


@router.post("/login")
async def login_submit(
    request: Request,
    username: str = Form(...),
    password: str = Form(...),
    next_url: str = Form(""),
):
    user = admin_db.verify_password(username, password)
    if not user:
        lang = _get_lang(request)
        t = i18n.get_translator(lang)
        return templates.TemplateResponse(
            "admin/login.html.j2",
            {
                "request": request,
                "t": t,
                "lang": lang,
                "root_path": settings.root_path,
                "error": "Invalid username or password",
                "next_url": next_url,
            },
        )
    request.session["user_id"] = user["id"]
    admin_db.log_action(user["id"], "login", f"User {username} logged in")
    redirect_to = _normalize_next_url(next_url)
    return RedirectResponse(redirect_to, status_code=303)


@router.get("/logout")
async def logout(request: Request):
    user = get_current_user(request)
    if user:
        admin_db.log_action(user["id"], "logout", f"User {user['username']} logged out")
    request.session.clear()
    return RedirectResponse(f"{settings.root_path}/admin/login", status_code=303)


# ── Dashboard ───────────────────────────────────────────────────


@router.get("/", response_class=HTMLResponse)
async def admin_dashboard(request: Request):
    user = get_current_user(request)
    if not user:
        return login_redirect(request)
    plugins = _get_all_plugins_info()
    enabled_count = sum(1 for p in plugins if p["enabled"])
    llm = admin_db.get_llm_settings(user["id"])
    llm_configured = bool(llm.get("api_key"))
    recent_logs = admin_db.get_recent_logs(10) if user.get("is_admin") else []
    usage_snapshot = get_result_store().get_api_usage_snapshot(since_days=30)
    return templates.TemplateResponse(
        "admin/dashboard.html.j2",
        _admin_context(
            request,
            user,
            plugin_count=len(plugins),
            enabled_count=enabled_count,
            llm_configured=llm_configured,
            recent_logs=recent_logs,
            usage_snapshot=usage_snapshot,
        ),
    )


# ── Plugin Management ──────────────────────────────────────────


@router.get("/plugins", response_class=HTMLResponse)
async def plugin_list(request: Request):
    user = get_current_user(request)
    if not user:
        return login_redirect(request)
    plugins = _get_all_plugins_info()
    msg = request.query_params.get("msg", "")
    return templates.TemplateResponse(
        "admin/plugins.html.j2",
        _admin_context(request, user, plugins=plugins, msg=msg),
    )


@router.post("/plugins/{name}/toggle")
async def plugin_toggle(request: Request, name: str):
    user = get_current_user(request)
    if not user:
        return login_redirect(request)
    config = _load_plugin_config()
    plugins = config.get("plugins", {})
    if name not in plugins:
        return RedirectResponse(
            f"{settings.root_path}/admin/plugins?msg=Plugin+not+found", status_code=303
        )
    current = plugins[name].get("enabled", True)
    plugins[name]["enabled"] = not current
    _save_plugin_config(config)
    action = "disabled" if current else "enabled"
    admin_db.log_action(user["id"], f"plugin_{action}", f"Plugin '{name}' {action}")
    return RedirectResponse(
        f"{settings.root_path}/admin/plugins?msg=Plugin+{name}+{action}",
        status_code=303,
    )


@router.post("/plugins/upload")
async def plugin_upload(request: Request, plugin_file: UploadFile = File(...)):
    """Upload a new community plugin (.py file) with AST validation."""
    user = get_current_user(request)
    if not user:
        return login_redirect(request)

    filename = plugin_file.filename or ""
    if not filename.endswith(".py"):
        return RedirectResponse(
            f"{settings.root_path}/admin/plugins?msg=Only+.py+files+are+accepted",
            status_code=303,
        )

    # Read content and validate
    content = await plugin_file.read()
    try:
        source_code = content.decode("utf-8")
    except UnicodeDecodeError:
        return RedirectResponse(
            f"{settings.root_path}/admin/plugins?msg=File+is+not+valid+UTF-8",
            status_code=303,
        )

    # AST validation: must parse, must contain a TIPlugin subclass
    validation = _validate_plugin_source(source_code)
    if not validation["ok"]:
        msg = validation["error"].replace(" ", "+")
        return RedirectResponse(
            f"{settings.root_path}/admin/plugins?msg={msg}",
            status_code=303,
        )

    # Save to plugins/community/
    community_dir = os.path.join(
        os.path.dirname(__file__), "..", "plugins", "community"
    )
    os.makedirs(community_dir, exist_ok=True)
    dest = os.path.join(community_dir, filename)

    if os.path.exists(dest):
        return RedirectResponse(
            f"{settings.root_path}/admin/plugins?msg=Plugin+{filename}+already+exists.+Delete+it+first.",
            status_code=303,
        )

    with open(dest, "w", encoding="utf-8") as f:
        f.write(source_code)

    # Auto-register in plugins.yaml with default config
    plugin_name = validation.get("plugin_name", filename.removesuffix(".py"))
    config = _load_plugin_config()
    plugins_cfg = config.setdefault("plugins", {})
    if plugin_name not in plugins_cfg:
        plugins_cfg[plugin_name] = {"enabled": True, "priority": 50, "config": {}}
        _save_plugin_config(config)

    admin_db.log_action(
        user["id"], "plugin_upload", f"Uploaded community plugin '{filename}'"
    )
    return RedirectResponse(
        f"{settings.root_path}/admin/plugins?msg=Plugin+{filename}+uploaded+successfully",
        status_code=303,
    )


@router.post("/plugins/{name}/delete")
async def plugin_delete(request: Request, name: str):
    """Delete a community plugin (builtin plugins cannot be deleted)."""
    user = get_current_user(request)
    if not user:
        return login_redirect(request)

    # Only allow deleting community plugins
    community_dir = os.path.join(
        os.path.dirname(__file__), "..", "plugins", "community"
    )
    matched_file = None
    if os.path.isdir(community_dir):
        for f in os.listdir(community_dir):
            if f.endswith(".py") and not f.startswith("_"):
                if f.removesuffix(".py") == name or f == name:
                    matched_file = os.path.join(community_dir, f)
                    break

    if not matched_file:
        return RedirectResponse(
            f"{settings.root_path}/admin/plugins?msg=Cannot+delete+builtin+plugins",
            status_code=303,
        )

    os.remove(matched_file)

    # Remove from plugins.yaml
    config = _load_plugin_config()
    plugins_cfg = config.get("plugins", {})
    if name in plugins_cfg:
        del plugins_cfg[name]
        _save_plugin_config(config)

    admin_db.log_action(
        user["id"], "plugin_delete", f"Deleted community plugin '{name}'"
    )
    return RedirectResponse(
        f"{settings.root_path}/admin/plugins?msg=Plugin+{name}+deleted",
        status_code=303,
    )


@router.get("/plugins/{name}/config", response_class=HTMLResponse)
async def plugin_config_page(request: Request, name: str):
    user = get_current_user(request)
    if not user:
        return login_redirect(request)
    config = _load_plugin_config()
    plugins = config.get("plugins", {})
    if name not in plugins:
        return RedirectResponse(f"{settings.root_path}/admin/plugins", status_code=303)
    plugin_cfg = plugins[name]
    # Get metadata too
    all_info = _get_all_plugins_info()
    plugin_info = next((p for p in all_info if p["name"] == name), {})
    msg = request.query_params.get("msg", "")
    return templates.TemplateResponse(
        "admin/plugin_config.html.j2",
        _admin_context(
            request,
            user,
            plugin_name=name,
            plugin_info=plugin_info,
            plugin_cfg=plugin_cfg,
            config_json=json.dumps(plugin_cfg.get("config", {}), indent=2),
            msg=msg,
        ),
    )


@router.post("/plugins/{name}/config")
async def plugin_config_save(
    request: Request,
    name: str,
    priority: int = Form(...),
    config_json: str = Form("{}"),
):
    user = get_current_user(request)
    if not user:
        return login_redirect(request)
    config = _load_plugin_config()
    plugins = config.get("plugins", {})
    if name not in plugins:
        return RedirectResponse(f"{settings.root_path}/admin/plugins", status_code=303)
    try:
        parsed_config = json.loads(config_json)
    except json.JSONDecodeError:
        return RedirectResponse(
            f"{settings.root_path}/admin/plugins/{name}/config?msg=Invalid+JSON",
            status_code=303,
        )
    plugins[name]["priority"] = priority
    plugins[name]["config"] = parsed_config
    _save_plugin_config(config)
    admin_db.log_action(
        user["id"], "plugin_config", f"Updated config for plugin '{name}'"
    )
    return RedirectResponse(
        f"{settings.root_path}/admin/plugins/{name}/config?msg=Configuration+saved",
        status_code=303,
    )


# ── LLM Settings ───────────────────────────────────────────────


@router.get("/settings/llm", response_class=HTMLResponse)
async def llm_settings_page(request: Request):
    user = get_current_user(request)
    if not user:
        return login_redirect(request)
    llm = admin_db.get_llm_settings(user["id"])
    can_use_personal_llm = admin_db.can_user_use_personal_llm(user["id"])
    shared_llm_configs = (
        admin_db.list_shared_llm_configs() if user.get("is_admin") else []
    )
    llm_usage_snapshot = get_result_store().get_api_usage_snapshot(
        user_id=None if user.get("is_admin") else user["id"],
        llm_source=None if user.get("is_admin") else llm.get("source"),
        include_shared_for_user=not user.get("is_admin"),
        since_days=30,
    )
    shared_usage_by_config = {
        int(item["shared_config_id"]): item
        for item in llm_usage_snapshot["llm_summary"]
        if item.get("shared_config_id") is not None
    }
    assignment = admin_db.get_user_llm_assignment(user["id"])
    msg = request.query_params.get("msg", "")
    return templates.TemplateResponse(
        "admin/llm_settings.html.j2",
        _admin_context(
            request,
            user,
            llm=llm,
            shared_llm_configs=shared_llm_configs,
            llm_usage_snapshot=llm_usage_snapshot,
            shared_usage_by_config=shared_usage_by_config,
            selected_shared_llm_id=(assignment or {}).get("llm_config_id"),
            can_use_personal_llm=can_use_personal_llm,
            msg=msg,
        ),
    )


@router.post("/settings/llm")
async def llm_settings_save(
    request: Request,
    api_key: str = Form(""),
    model: str = Form("gpt-4o"),
    base_url: str = Form("https://api.openai.com/v1"),
    shared_llm_config_id: str = Form(""),
):
    user = get_current_user(request)
    if not user:
        return login_redirect(request)
    selected_shared_id = None
    if user.get("is_admin") and shared_llm_config_id.strip():
        selected_shared_id = int(shared_llm_config_id)

    if user.get("is_admin") or admin_db.can_user_use_personal_llm(user["id"]):
        admin_db.save_llm_settings(
            user["id"], api_key=api_key, model=model, base_url=base_url
        )

    if user.get("is_admin"):
        admin_db.assign_shared_llm_to_user(user["id"], selected_shared_id)
    admin_db.log_action(
        user["id"], "llm_settings", f"Updated LLM settings (model: {model})"
    )
    return RedirectResponse(
        f"{settings.root_path}/admin/settings/llm?msg=LLM+settings+saved",
        status_code=303,
    )


@router.post("/settings/llm/shared")
async def shared_llm_save(
    request: Request,
    name: str = Form(...),
    api_key: str = Form(""),
    model: str = Form(...),
    base_url: str = Form(...),
    config_id: str = Form(""),
    is_active: str = Form("true"),
):
    user = get_current_user(request)
    if not user or not user.get("is_admin"):
        return login_redirect(request)
    admin_db.save_shared_llm_config(
        name=name.strip(),
        api_key=api_key,
        model=model.strip(),
        base_url=base_url.strip(),
        config_id=int(config_id) if config_id.strip() else None,
        is_active=_parse_bool(is_active),
    )
    admin_db.log_action(
        user["id"], "shared_llm_save", f"Saved shared LLM config '{name}'"
    )
    return RedirectResponse(
        f"{settings.root_path}/admin/settings/llm?msg=Shared+LLM+config+saved",
        status_code=303,
    )


@router.post("/settings/llm/shared/{config_id}/delete")
async def shared_llm_delete(request: Request, config_id: int):
    user = get_current_user(request)
    if not user or not user.get("is_admin"):
        return login_redirect(request)
    admin_db.delete_shared_llm_config(config_id)
    admin_db.log_action(
        user["id"], "shared_llm_delete", f"Deleted shared LLM config #{config_id}"
    )
    return RedirectResponse(
        f"{settings.root_path}/admin/settings/llm?msg=Shared+LLM+config+deleted",
        status_code=303,
    )


@router.post("/api/llm/validate")
async def llm_validate(request: Request):
    """Validate LLM API key and base URL, return available models."""
    user = get_current_user(request)
    if not user:
        return JSONResponse(
            {"ok": False, "error": "Not authenticated"}, status_code=401
        )
    if not user.get("is_admin") and not admin_db.can_user_use_personal_llm(user["id"]):
        return JSONResponse(
            {
                "ok": False,
                "error": "Personal LLM configuration is disabled by admin policy",
            },
            status_code=403,
        )
    try:
        body = await request.json()
    except Exception:
        return JSONResponse({"ok": False, "error": "Invalid JSON"}, status_code=400)
    api_key = body.get("api_key", "").strip()
    base_url = body.get("base_url", "").strip()
    if not api_key or not base_url:
        return JSONResponse({"ok": False, "error": "API key and base URL are required"})
    from app.llm_client import llm_client

    result = await llm_client.validate_connection(api_key, base_url)
    return JSONResponse(result)


@router.get("/api/llm/models")
async def llm_models(request: Request):
    """Return available models for the current user's saved LLM settings."""
    user = get_current_user(request)
    if not user:
        return JSONResponse(
            {"ok": False, "error": "Not authenticated"}, status_code=401
        )
    if not user.get("is_admin") and not admin_db.can_user_use_personal_llm(user["id"]):
        return JSONResponse(
            {
                "ok": False,
                "models": [],
                "error": "Personal LLM configuration is disabled by admin policy",
            },
            status_code=403,
        )
    llm = admin_db.get_llm_settings(user["id"])
    api_key = llm.get("api_key", "").strip()
    base_url = llm.get("base_url", "").strip()
    if not api_key or not base_url:
        return JSONResponse({"ok": False, "models": [], "error": "LLM not configured"})
    from app.llm_client import llm_client

    result = await llm_client.validate_connection(api_key, base_url)
    return JSONResponse(result)


# ── User Profile ───────────────────────────────────────────────


@router.get("/profile", response_class=HTMLResponse)
async def profile_page(request: Request):
    user = get_current_user(request)
    if not user:
        return login_redirect(request)
    msg = request.query_params.get("msg", "")
    usage_snapshot = get_result_store().get_api_usage_snapshot(
        user_id=user["id"],
        include_shared_for_user=True,
        since_days=30,
    )
    return templates.TemplateResponse(
        "admin/profile.html.j2",
        _admin_context(request, user, msg=msg, usage_snapshot=usage_snapshot),
    )


@router.post("/profile")
async def profile_update(
    request: Request,
    display_name: str = Form(...),
):
    user = get_current_user(request)
    if not user:
        return login_redirect(request)
    admin_db.update_profile(user["id"], display_name=display_name)
    admin_db.log_action(
        user["id"], "profile_update", f"Updated display name to '{display_name}'"
    )
    return RedirectResponse(
        f"{settings.root_path}/admin/profile?msg=Profile+updated",
        status_code=303,
    )


@router.post("/profile/password")
async def password_change(
    request: Request,
    current_password: str = Form(...),
    new_password: str = Form(...),
    confirm_password: str = Form(...),
):
    user = get_current_user(request)
    if not user:
        return login_redirect(request)
    if new_password != confirm_password:
        return RedirectResponse(
            f"{settings.root_path}/admin/profile?msg=Passwords+do+not+match",
            status_code=303,
        )
    if len(new_password) < 4:
        return RedirectResponse(
            f"{settings.root_path}/admin/profile?msg=Password+too+short+(min+4)",
            status_code=303,
        )
    # Verify current password
    verified = admin_db.verify_password(user["username"], current_password)
    if not verified:
        return RedirectResponse(
            f"{settings.root_path}/admin/profile?msg=Current+password+is+incorrect",
            status_code=303,
        )
    admin_db.update_password(user["id"], new_password)
    admin_db.log_action(user["id"], "password_change", "Password changed")
    return RedirectResponse(
        f"{settings.root_path}/admin/profile?msg=Password+changed+successfully",
        status_code=303,
    )


# ── User Management (admin only) ──────────────────────────────


@router.get("/users", response_class=HTMLResponse)
async def user_list(request: Request):
    user = get_current_user(request)
    if not user or not user.get("is_admin"):
        return login_redirect(request)
    users = admin_db.list_users()
    groups = admin_db.list_groups()
    plugin_permissions = {
        u["id"]: admin_db.list_user_plugin_permissions(u["id"]) for u in users
    }
    user_personal_llm_permissions = {
        u["id"]: admin_db.get_user_personal_llm_permission(u["id"]) for u in users
    }
    llm_allowlists = {u["id"]: admin_db.list_user_llm_allowlist(u["id"]) for u in users}
    shared_llm_configs = admin_db.list_shared_llm_configs()
    msg = request.query_params.get("msg", "")
    return templates.TemplateResponse(
        "admin/users.html.j2",
        _admin_context(
            request,
            user,
            users=users,
            groups=groups,
            plugin_permissions=plugin_permissions,
            user_personal_llm_permissions=user_personal_llm_permissions,
            llm_allowlists=llm_allowlists,
            shared_llm_configs=shared_llm_configs,
            plugins=PLUGIN_API_KEY_REGISTRY,
            msg=msg,
        ),
    )


@router.post("/users/create")
async def user_create(
    request: Request,
    username: str = Form(...),
    password: str = Form(...),
    display_name: str = Form(""),
    is_admin: bool = Form(False),
    group_ids: list[str] = Form([]),
    shared_llm_config_id: str = Form(""),
):
    user = get_current_user(request)
    if not user or not user.get("is_admin"):
        return login_redirect(request)
    try:
        created_id = admin_db.create_user(
            username=username,
            password=password,
            display_name=display_name or username,
            is_admin=is_admin,
        )
        admin_db.set_user_groups(created_id, _parse_int_list(group_ids))
        admin_db.assign_shared_llm_to_user(
            created_id,
            int(shared_llm_config_id) if shared_llm_config_id.strip() else None,
        )
        admin_db.log_action(user["id"], "user_create", f"Created user '{username}'")
        return RedirectResponse(
            f"{settings.root_path}/admin/users?msg=User+{username}+created",
            status_code=303,
        )
    except Exception as e:
        return RedirectResponse(
            f"{settings.root_path}/admin/users?msg=Error:+{e}",
            status_code=303,
        )


@router.post("/users/{user_id}/delete")
async def user_delete(request: Request, user_id: int):
    user = get_current_user(request)
    if not user or not user.get("is_admin"):
        return login_redirect(request)
    if user_id == user["id"]:
        return RedirectResponse(
            f"{settings.root_path}/admin/users?msg=Cannot+delete+yourself",
            status_code=303,
        )
    target = admin_db.get_user_by_id(user_id)
    if target:
        admin_db.delete_user(user_id)
        admin_db.log_action(
            user["id"], "user_delete", f"Deleted user '{target['username']}'"
        )
    return RedirectResponse(
        f"{settings.root_path}/admin/users?msg=User+deleted",
        status_code=303,
    )


@router.post("/users/{user_id}/edit")
async def user_edit(
    request: Request,
    user_id: int,
    display_name: str = Form(...),
    is_admin: str = Form("false"),
    is_active: str = Form("true"),
    group_ids: list[str] = Form([]),
    shared_llm_config_id: str = Form(""),
):
    user = get_current_user(request)
    if not user or not user.get("is_admin"):
        return login_redirect(request)
    target = admin_db.get_user_by_id(user_id)
    if not target:
        return RedirectResponse(
            f"{settings.root_path}/admin/users?msg=User+not+found",
            status_code=303,
        )
    new_is_admin = _parse_bool(is_admin)
    new_is_active = _parse_bool(is_active)
    if target["is_admin"] and (not new_is_admin or not new_is_active):
        if admin_db.count_admin_users() <= 1:
            return RedirectResponse(
                f"{settings.root_path}/admin/users?msg=Cannot+disable+or+demote+the+last+active+admin",
                status_code=303,
            )
    if user_id == user["id"] and not new_is_active:
        return RedirectResponse(
            f"{settings.root_path}/admin/users?msg=Cannot+disable+yourself",
            status_code=303,
        )
    admin_db.update_user(
        user_id,
        display_name=display_name,
        is_admin=new_is_admin,
        is_active=new_is_active,
    )
    admin_db.set_user_groups(user_id, _parse_int_list(group_ids))
    admin_db.assign_shared_llm_to_user(
        user_id,
        int(shared_llm_config_id) if shared_llm_config_id.strip() else None,
    )
    admin_db.log_action(user["id"], "user_edit", f"Updated user '{target['username']}'")
    return RedirectResponse(
        f"{settings.root_path}/admin/users?msg=User+updated",
        status_code=303,
    )


@router.post("/users/{user_id}/permissions")
async def user_plugin_permissions_save(request: Request, user_id: int):
    user = get_current_user(request)
    if not user or not user.get("is_admin"):
        return login_redirect(request)
    form = await request.form()
    personal_llm_value = form.get("personal_llm")
    personal_llm_allowed = (
        None
        if personal_llm_value == "inherit" or personal_llm_value is None
        else _parse_bool(str(personal_llm_value))
    )
    admin_db.set_user_personal_llm_permission(user_id, personal_llm_allowed)
    for plugin in PLUGIN_API_KEY_REGISTRY:
        field = f"perm_{plugin['plugin_name']}"
        value = form.get(field)
        allowed = (
            None if value == "inherit" or value is None else _parse_bool(str(value))
        )
        admin_db.set_user_plugin_permission(user_id, plugin["plugin_name"], allowed)
    for item in admin_db.list_shared_llm_configs():
        field = f"llmperm_{item['id']}"
        value = form.get(field)
        allowed = (
            None if value == "inherit" or value is None else _parse_bool(str(value))
        )
        admin_db.set_user_llm_allowlist(user_id, int(item["id"]), allowed)
    admin_db.log_action(
        user["id"],
        "user_plugin_permissions",
        f"Updated shared-plugin and shared-LLM permissions for user #{user_id}",
    )
    return RedirectResponse(
        f"{settings.root_path}/admin/users?msg=User+permissions+updated",
        status_code=303,
    )


@router.get("/groups", response_class=HTMLResponse)
async def group_list(request: Request):
    user = get_current_user(request)
    if not user or not user.get("is_admin"):
        return login_redirect(request)
    groups = admin_db.list_groups()
    shared_llm_configs = admin_db.list_shared_llm_configs()
    group_personal_llm_permissions = {
        group["id"]: admin_db.get_group_personal_llm_permission(group["id"])
        for group in groups
    }
    group_llm_allowlists = {
        group["id"]: admin_db.list_group_llm_allowlist(group["id"]) for group in groups
    }
    msg = request.query_params.get("msg", "")
    return templates.TemplateResponse(
        "admin/groups.html.j2",
        _admin_context(
            request,
            user,
            groups=groups,
            plugins=PLUGIN_API_KEY_REGISTRY,
            shared_llm_configs=shared_llm_configs,
            group_personal_llm_permissions=group_personal_llm_permissions,
            group_llm_allowlists=group_llm_allowlists,
            msg=msg,
        ),
    )


@router.post("/groups/create")
async def group_create(
    request: Request,
    name: str = Form(...),
    description: str = Form(""),
    priority: int = Form(100),
):
    user = get_current_user(request)
    if not user or not user.get("is_admin"):
        return login_redirect(request)
    admin_db.create_group(
        name=name.strip(), description=description.strip(), priority=priority
    )
    admin_db.log_action(user["id"], "group_create", f"Created policy group '{name}'")
    return RedirectResponse(
        f"{settings.root_path}/admin/groups?msg=Group+created",
        status_code=303,
    )


@router.post("/groups/{group_id}/edit")
async def group_edit(request: Request, group_id: int):
    user = get_current_user(request)
    if not user or not user.get("is_admin"):
        return login_redirect(request)
    form = await request.form()
    name = str(form.get("name", "")).strip()
    description = str(form.get("description", "")).strip()
    priority = int(str(form.get("priority", "100") or "100"))
    llm_config_id = str(form.get("shared_llm_config_id", "")).strip()
    personal_llm_value = form.get("personal_llm")
    personal_llm_allowed = (
        None
        if personal_llm_value == "inherit" or personal_llm_value is None
        else _parse_bool(str(personal_llm_value))
    )
    group_plugin_permissions = {}
    admin_db.set_group_personal_llm_permission(group_id, personal_llm_allowed)
    for plugin in PLUGIN_API_KEY_REGISTRY:
        field = f"perm_{plugin['plugin_name']}"
        value = form.get(field)
        allowed = (
            None if value == "inherit" or value is None else _parse_bool(str(value))
        )
        admin_db.set_group_plugin_permission(group_id, plugin["plugin_name"], allowed)
        group_plugin_permissions[plugin["plugin_name"]] = allowed
    for item in admin_db.list_shared_llm_configs():
        field = f"llmperm_{item['id']}"
        value = form.get(field)
        allowed = (
            None if value == "inherit" or value is None else _parse_bool(str(value))
        )
        admin_db.set_group_llm_allowlist(group_id, int(item["id"]), allowed)
    admin_db.update_group(
        group_id, name=name, description=description, priority=priority
    )
    admin_db.assign_shared_llm_to_group(
        group_id, int(llm_config_id) if llm_config_id else None
    )
    admin_db.log_action(user["id"], "group_edit", f"Updated policy group '{name}'")
    return RedirectResponse(
        f"{settings.root_path}/admin/groups?msg=Group+updated",
        status_code=303,
    )


@router.post("/groups/{group_id}/delete")
async def group_delete(request: Request, group_id: int):
    user = get_current_user(request)
    if not user or not user.get("is_admin"):
        return login_redirect(request)
    admin_db.delete_group(group_id)
    admin_db.log_action(user["id"], "group_delete", f"Deleted policy group #{group_id}")
    return RedirectResponse(
        f"{settings.root_path}/admin/groups?msg=Group+deleted",
        status_code=303,
    )


# ── Log Viewer ─────────────────────────────────────────────────


@router.get("/logs", response_class=HTMLResponse)
async def log_viewer(request: Request):
    user = get_current_user(request)
    if not user:
        return login_redirect(request)
    return templates.TemplateResponse(
        "admin/logs.html.j2",
        _admin_context(request, user),
    )


@router.get("/api/logs")
async def log_entries(request: Request):
    """Return current log entries as JSON (initial batch)."""
    user = get_current_user(request)
    if not user:
        return JSONResponse(
            {"ok": False, "error": "Not authenticated"}, status_code=401
        )
    since_id = int(request.query_params.get("since_id", "0"))
    level = request.query_params.get("level", "")
    search = request.query_params.get("search", "")
    store = LogStore()
    entries = store.get_entries(
        since_id=since_id, level=level or None, search=search or None
    )
    return JSONResponse({"ok": True, "entries": entries})


@router.get("/api/logs/stream")
async def log_stream(request: Request):
    """SSE endpoint streaming live log entries."""
    user = get_current_user(request)
    if not user:
        return JSONResponse(
            {"ok": False, "error": "Not authenticated"}, status_code=401
        )
    level = request.query_params.get("level", "") or None
    since_id = int(request.query_params.get("since_id", "0") or "0")

    async def _generate():
        store = LogStore()
        async for entry in store.stream(since_id=since_id, level=level):
            if await request.is_disconnected():
                break
            if entry.get("keepalive"):
                yield ": keepalive\n\n"
            else:
                yield f"id: {entry['id']}\nevent: log\ndata: {json.dumps(entry)}\n\n"

    return StreamingResponse(_generate(), media_type="text/event-stream")


# ── Plugin API Key Registry ────────────────────────────────────
# Known built-in plugins and their API key application URLs.
# This helps users quickly register for API keys.

PLUGIN_API_KEY_REGISTRY: list[dict[str, Any]] = [
    {
        "plugin_name": "abuseipdb",
        "display_name": "AbuseIPDB",
        "env_var": "ABUSEIPDB_API_KEY",
        "apply_url": "https://www.abuseipdb.com/account/api",
        "free_tier": True,
        "note": "Free tier: 1000 checks/day",
    },
    {
        "plugin_name": "virustotal",
        "display_name": "VirusTotal",
        "env_var": "VT_API_KEY",
        "apply_url": "https://www.virustotal.com/gui/my-apikey",
        "free_tier": True,
        "note": "Free tier: 4 lookups/min, 500/day",
    },
    {
        "plugin_name": "otx",
        "display_name": "AlienVault OTX",
        "env_var": "OTX_API_KEY",
        "apply_url": "https://otx.alienvault.com/api",
        "free_tier": True,
        "note": "Free, requires registration",
    },
    {
        "plugin_name": "greynoise",
        "display_name": "GreyNoise",
        "env_var": "GREYNOISE_API_KEY",
        "apply_url": "https://viz.greynoise.io/account/api-key",
        "free_tier": True,
        "note": "Community API: free, limited features",
    },
    {
        "plugin_name": "shodan",
        "display_name": "Shodan",
        "env_var": "SHODAN_API_KEY",
        "apply_url": "https://account.shodan.io/",
        "free_tier": True,
        "note": "Free tier available with limited queries",
    },
    {
        "plugin_name": "threatbook",
        "display_name": "ThreatBook (微步在线)",
        "env_var": "THREATBOOK_API_KEY",
        "apply_url": "https://x.threatbook.com/v5/myApi",
        "free_tier": True,
        "note": "Free tier: 50 queries/day",
    },
    {
        "plugin_name": "tianjiyoumeng",
        "display_name": "TianJi YouMeng (天际友盟)",
        "env_var": "TIANJIYOUMENG_API_KEY",
        "apply_url": "https://redqueen.tj-un.com",
        "free_tier": False,
        "note": "Enterprise-only, contact service@tj-un.com",
    },
]


def _get_plugin_key_registry() -> list[dict[str, Any]]:
    """Return the plugin API key registry with current configuration status."""
    shared_keys = {
        row["plugin_name"] for row in get_result_store().list_plugin_api_keys(user_id=0)
    }
    registry = []
    for entry in PLUGIN_API_KEY_REGISTRY:
        info = dict(entry)
        info["shared_configured"] = entry["plugin_name"] in shared_keys
        registry.append(info)
    return registry


# ── Plugin API Key Management (Admin — Shared Keys) ────────────


@router.get("/settings/api-keys", response_class=HTMLResponse)
async def api_keys_admin_page(request: Request):
    """Admin page for managing shared plugin API keys and policy."""
    user = get_current_user(request)
    if not user:
        return login_redirect(request)
    if not user.get("is_admin"):
        return RedirectResponse(f"{settings.root_path}/admin/", status_code=303)

    shared_keys = get_result_store().list_plugin_api_keys(user_id=0)
    shared_keys_allowed = get_result_store().is_shared_keys_allowed()
    configured_only = get_result_store().is_configured_only()
    registry = _get_plugin_key_registry()
    usage_snapshot = get_result_store().get_api_usage_snapshot(
        plugin_key_scope="shared",
        llm_source=None,
        since_days=30,
    )
    plugin_usage_by_name = {
        row["plugin_name"]: row for row in usage_snapshot["plugin_summary"]
    }
    msg = request.query_params.get("msg", "")

    return templates.TemplateResponse(
        "admin/api_keys_admin.html.j2",
        _admin_context(
            request,
            user,
            shared_keys=shared_keys,
            shared_keys_allowed=shared_keys_allowed,
            configured_only=configured_only,
            registry=registry,
            usage_snapshot=usage_snapshot,
            plugin_usage_by_name=plugin_usage_by_name,
            msg=msg,
        ),
    )


@router.post("/settings/api-keys/shared")
async def api_keys_admin_save(
    request: Request,
    plugin_name: str = Form(...),
    api_key: str = Form(""),
):
    """Save or update a shared (admin) plugin API key."""
    user = get_current_user(request)
    if not user or not user.get("is_admin"):
        return login_redirect(request)

    if not api_key.strip():
        return RedirectResponse(
            f"{settings.root_path}/admin/settings/api-keys?msg=API+key+cannot+be+empty",
            status_code=303,
        )

    get_result_store().save_plugin_api_key(
        user_id=0, plugin_name=plugin_name, api_key=api_key.strip()
    )
    admin_db.log_action(
        user["id"],
        "shared_api_key_save",
        f"Updated shared API key for plugin '{plugin_name}'",
    )
    return RedirectResponse(
        f"{settings.root_path}/admin/settings/api-keys?msg=Shared+API+key+for+{plugin_name}+saved",
        status_code=303,
    )


@router.post("/settings/api-keys/shared/{plugin_name}/delete")
async def api_keys_admin_delete(request: Request, plugin_name: str):
    """Delete a shared (admin) plugin API key."""
    user = get_current_user(request)
    if not user or not user.get("is_admin"):
        return login_redirect(request)

    get_result_store().delete_plugin_api_key(user_id=0, plugin_name=plugin_name)
    admin_db.log_action(
        user["id"],
        "shared_api_key_delete",
        f"Deleted shared API key for plugin '{plugin_name}'",
    )
    return RedirectResponse(
        f"{settings.root_path}/admin/settings/api-keys?msg=Shared+key+for+{plugin_name}+deleted",
        status_code=303,
    )


@router.post("/settings/api-keys/policy")
async def api_keys_policy_toggle(
    request: Request,
    allow_shared: bool = Form(False),
    configured_only: bool = Form(False),
):
    """Toggle whether regular users may use admin-configured shared keys."""
    user = get_current_user(request)
    if not user or not user.get("is_admin"):
        return login_redirect(request)

    get_result_store().set_shared_keys_policy(allow_shared)
    get_result_store().set_configured_only_policy(configured_only)
    status = "allowed" if allow_shared else "disallowed"
    admin_db.log_action(
        user["id"], "shared_key_policy", f"Shared key usage {status} for regular users"
    )
    return RedirectResponse(
        f"{settings.root_path}/admin/settings/api-keys?msg=Shared+key+policy+updated:+{status}",
        status_code=303,
    )


# ── Plugin API Key Management (User — Personal Keys) ──────────


@router.get("/settings/my-api-keys", response_class=HTMLResponse)
async def api_keys_user_page(request: Request):
    """User page for managing personal plugin API keys."""
    user = get_current_user(request)
    if not user:
        return login_redirect(request)

    user_keys = get_result_store().list_plugin_api_keys(user_id=user["id"])
    registry = _get_plugin_key_registry()
    shared_access = {
        plugin["plugin_name"]: admin_db.can_user_use_shared_plugin(
            user["id"], plugin["plugin_name"]
        )
        for plugin in PLUGIN_API_KEY_REGISTRY
    }
    usage_snapshot = get_result_store().get_api_usage_snapshot(
        user_id=user["id"],
        include_shared_for_user=True,
        since_days=30,
    )
    plugin_usage_by_name = {
        row["plugin_name"]: row for row in usage_snapshot["plugin_summary"]
    }
    msg = request.query_params.get("msg", "")

    return templates.TemplateResponse(
        "admin/api_keys_user.html.j2",
        _admin_context(
            request,
            user,
            user_keys=user_keys,
            registry=registry,
            shared_access=shared_access,
            usage_snapshot=usage_snapshot,
            plugin_usage_by_name=plugin_usage_by_name,
            msg=msg,
        ),
    )


@router.get("/api/usage")
async def api_usage_json(request: Request):
    """Return scoped API usage statistics for the current user/admin."""
    user = get_current_user(request)
    if not user:
        return JSONResponse(
            {"ok": False, "error": "Not authenticated"}, status_code=401
        )

    if user.get("is_admin"):
        payload = get_result_store().get_api_usage_snapshot(since_days=30)
    else:
        payload = get_result_store().get_api_usage_snapshot(
            user_id=user["id"],
            include_shared_for_user=True,
            since_days=30,
        )

    return JSONResponse({"ok": True, **payload})


@router.get("/usage", response_class=HTMLResponse)
async def api_usage_page(request: Request):
    """Admin HTML page for API usage statistics."""
    user = get_current_user(request)
    if not user:
        return login_redirect(request)
    if not user.get("is_admin"):
        return RedirectResponse(f"{settings.root_path}/admin/profile", status_code=303)

    usage_snapshot = get_result_store().get_api_usage_snapshot(since_days=30)
    return templates.TemplateResponse(
        "admin/usage.html.j2",
        _admin_context(request, user, usage_snapshot=usage_snapshot),
    )


@router.post("/settings/my-api-keys")
async def api_keys_user_save(
    request: Request,
    plugin_name: str = Form(...),
    api_key: str = Form(""),
):
    """Save or update a personal plugin API key for the current user."""
    user = get_current_user(request)
    if not user:
        return login_redirect(request)

    if not api_key.strip():
        return RedirectResponse(
            f"{settings.root_path}/admin/settings/my-api-keys?msg=API+key+cannot+be+empty",
            status_code=303,
        )

    get_result_store().save_plugin_api_key(
        user_id=user["id"], plugin_name=plugin_name, api_key=api_key.strip()
    )
    admin_db.log_action(
        user["id"],
        "personal_api_key_save",
        f"Updated personal API key for plugin '{plugin_name}'",
    )
    return RedirectResponse(
        f"{settings.root_path}/admin/settings/my-api-keys?msg=API+key+for+{plugin_name}+saved",
        status_code=303,
    )


@router.post("/settings/my-api-keys/{plugin_name}/delete")
async def api_keys_user_delete(request: Request, plugin_name: str):
    """Delete a personal plugin API key."""
    user = get_current_user(request)
    if not user:
        return login_redirect(request)

    get_result_store().delete_plugin_api_key(
        user_id=user["id"], plugin_name=plugin_name
    )
    admin_db.log_action(
        user["id"],
        "personal_api_key_delete",
        f"Deleted personal API key for plugin '{plugin_name}'",
    )
    return RedirectResponse(
        f"{settings.root_path}/admin/settings/my-api-keys?msg=API+key+for+{plugin_name}+deleted",
        status_code=303,
    )
