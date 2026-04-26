from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    ml_service_port: int = 8086
    kafka_bootstrap_servers: str = "kafka:9092"
    kafka_input_topic: str = "threat-alerts"
    kafka_output_topic: str = "ml-threat-detections"
    kafka_consumer_group: str = "ml-detection-consumer"
    model_dir: str = "/app/models"
    ml_confidence_threshold: float = 0.3
    ml_default_weight: float = 1.0
    database_url: str = (
        "postgresql://threat_user:threat_password@postgres:5432/threat_detection"
    )
    log_level: str = "INFO"

    # BiGRU temporal model settings
    bigru_enabled: bool = False
    bigru_hidden_size: int = 64
    bigru_num_layers: int = 2
    bigru_dropout: float = 0.3
    bigru_max_seq_len_tier1: int = 32
    bigru_max_seq_len_tier2: int = 32
    bigru_max_seq_len_tier3: int = 48
    bigru_min_seq_len: int = 4
    bigru_ensemble_alpha: float = 0.6
    bigru_buffer_max_entries: int = 10_000
    bigru_buffer_ttl_tier1: int = 1800
    bigru_buffer_ttl_tier2: int = 10800
    bigru_buffer_ttl_tier3: int = 86400

    # Phase 4A: Alpha optimization
    alpha_search_values: str = "0.3,0.4,0.5,0.6,0.7,0.8"

    # Per-customer model training
    per_customer_min_samples: int = 200

    # Phase 4C: Model hot-reload
    model_watch_enabled: bool = False
    model_watch_interval_seconds: int = 30

    # Phase 4D: Drift detection
    drift_enabled: bool = True
    drift_psi_threshold: float = 0.2
    drift_window_size: int = 500
    drift_baseline_path: str = "/app/models/drift_baselines"

    # Phase 4E: Shadow scoring (champion/challenger)
    shadow_scoring_enabled: bool = False
    challenger_model_dir: str = "/app/models/challenger"

    model_config = SettingsConfigDict(
        env_file=".env", protected_namespaces=("settings_",)
    )


settings = Settings()
