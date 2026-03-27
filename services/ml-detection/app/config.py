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

    model_config = SettingsConfigDict(env_file=".env", protected_namespaces=("settings_",))


settings = Settings()
