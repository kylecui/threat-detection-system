from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    ml_service_port: int = 8086
    kafka_bootstrap_servers: str = "kafka:29092"
    kafka_input_topic: str = "threat-alerts"
    kafka_output_topic: str = "ml-threat-detections"
    kafka_consumer_group: str = "ml-detection-consumer"
    model_dir: str = "/app/models"
    ml_confidence_threshold: float = 0.3
    ml_default_weight: float = 1.0
    database_url: str = "postgresql://threat_user:threat_password@postgres:5432/threat_detection"
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

    model_config = SettingsConfigDict(env_file=".env", protected_namespaces=("settings_",))


settings = Settings()
