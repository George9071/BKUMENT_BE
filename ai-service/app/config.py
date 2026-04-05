from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    UNSTRUCTURED_API_KEY: str
    UNSTRUCTURED_API_URL: str

    GEMINI_API_KEY: str
    GEMINI_MODEL: str
    MAX_FILE_SIZE: int = 50 * 1024 * 1024
    
    PROFILE_SERVICE_URL: str
    
    OPENROUTER_API_KEY: str
    OPENROUTER_MODEL: str

    DB_HOST: str
    DB_PORT: int
    DB_NAME: str
    POSTGRES_USERNAME: str
    POSTGRES_PASSWORD: str

    class Config:
        env_file = "../.env"
        case_sensitive = True
        extra="ignore"


@lru_cache()
def get_settings() -> Settings:
    return Settings()
