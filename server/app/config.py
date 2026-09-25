from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    db_host: str = "mysql"
    db_port: int = 3306
    db_name: str = "agilitycc"
    db_user: str = "agilitycc"
    db_password: str = "changeme"

    app_secret: str = "changeme"
    app_url: str = "http://localhost"
    api_port: int = 8090

    public_read: bool = False
    log_level: str = "info"

    hiscores_enabled: bool = True
    items_enabled: bool = True
    user_agent: str = "AgilityCCTracker/1.0"


settings = Settings()
