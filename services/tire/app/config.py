"""
Configuration management for Threat Intelligence Reasoning Engine.

v2.0: Per-source API key fields removed. Plugins resolve their own
API keys from environment variables declared in plugin metadata.
"""

from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""

    model_config = SettingsConfigDict(
        env_file=".env",
        case_sensitive=False,
        extra="ignore",
    )

    # Legacy collector API keys
    # Kept for backward compatibility with v1 collector code and tests.
    abuseipdb_api_key: Optional[str] = None
    otx_api_key: Optional[str] = None
    greynoise_api_key: Optional[str] = None
    vt_api_key: Optional[str] = None
    shodan_api_key: Optional[str] = None

    # Cache settings
    cache_ttl_hours: int = 24

    # HTTP settings
    http_timeout_seconds: int = 15
    max_retries: int = 2

    # Logging
    log_level: str = "INFO"

    # Language
    language: str = "en"

    # Deployment: sub-path prefix (e.g. "/v2" when behind a reverse proxy)
    root_path: str = ""

    # LLM settings (for narrative report generation)
    llm_api_key: Optional[str] = None
    llm_model: str = "gpt-4o"
    llm_base_url: str = "https://api.openai.com/v1"

    # Admin portal session secret
    session_secret_key: str = "change-me-in-production"

    # Persistence: result staleness threshold (days)
    result_staleness_days: int = 7

    # Fernet encryption key for API keys at rest
    tire_fernet_key: Optional[str] = None


# Global settings instance
settings = Settings()
