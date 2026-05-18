from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    service_name: str = "rag-ai-service"
    service_version: str = "0.1.0"

    embedding_provider: str = "aliyun-bailian-openai-compatible"
    embedding_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    embedding_api_key: str = ""
    embedding_default_model: str = "text-embedding-v4"
    embedding_path: str = "/embeddings"

    chat_provider: str = "deepseek-openai-compatible"
    chat_base_url: str = "https://api.deepseek.com"
    chat_api_key: str = ""
    chat_default_model: str = "deepseek-v4-pro"
    chat_path: str = "/chat/completions"

    http_connect_timeout_ms: int = 5000
    http_read_timeout_ms: int = 30000


@lru_cache
def get_settings() -> Settings:
    return Settings()
