from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    UNSTRUCTURED_API_KEY: str
    UNSTRUCTURED_API_URL: str

    GEMINI_API_KEY: str
    GEMINI_MODEL: str
    APP_NAME: str
    APP_VERSION: str
    MAX_FILE_SIZE: int = 50 * 1024 * 1024
    
    PROFILE_SERVICE_URL: str

    DB_HOST: str
    DB_PORT: int
    DB_NAME: str
    DB_USER: str
    DB_PASSWORD: str

    class Config:
        env_file = ".env"
        case_sensitive = True


@lru_cache()
def get_settings() -> Settings:
    return Settings()
