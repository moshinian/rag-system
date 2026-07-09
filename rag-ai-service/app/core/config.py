from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """集中承载 AI Gateway 的运行时配置。"""

    # 支持从 .env 读取，并忽略当前阶段未显式声明的额外环境变量。
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    service_name: str = "rag-ai-service"
    service_version: str = "0.1.0"

    # embedding 能力的上游接入配置。
    embedding_provider: str = "aliyun-bailian-openai-compatible"
    embedding_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    embedding_api_key: str = Field(default="", validation_alias="EMBEDDING_API_KEY")
    embedding_default_model: str = "text-embedding-v4"
    embedding_path: str = "/embeddings"

    # chat 能力的上游接入配置。
    chat_provider: str = "deepseek-openai-compatible"
    chat_base_url: str = "https://api.deepseek.com"
    chat_api_key: str = Field(default="", validation_alias="CHAT_API_KEY")
    chat_default_model: str = "deepseek-v4-pro"
    chat_path: str = "/chat/completions"

    # provider-specific 兼容变量，避免本地沿用旧 .env 时出现“有 key 但没接上”的问题。
    dashscope_api_key: str = Field(default="", validation_alias="DASHSCOPE_API_KEY", exclude=True)
    deepseek_api_key: str = Field(default="", validation_alias="DEEPSEEK_API_KEY", exclude=True)
    openai_api_key: str = Field(default="", validation_alias="OPENAI_API_KEY", exclude=True)

    # 连接超时主要覆盖建连和连接池等待，读超时覆盖上游生成阶段。
    http_connect_timeout_ms: int = 5000
    http_read_timeout_ms: int = 30000

    # Agent Runtime 调 Java MCP tools capability 的配置。
    agent_tool_client: str = "mcp"
    # Agent planner 唯一使用真实 LLM；失败时 run 明确 FAILED，不做规则型 fallback。
    agent_planner_model: str = ""
    agent_planner_temperature: float = 0
    agent_planner_timeout_ms: int = 30000
    # recorder 为默认稳定路径；langgraph 启用 native custom/updates stream adapter。
    agent_streaming_mode: str = "recorder"
    mcp_tool_base_url: str = "http://127.0.0.1:8080"
    mcp_tool_endpoint: str = "/api/internal/mcp"
    mcp_tool_token: str = "dev-agent-tool-token"
    mcp_protocol_version: str = "2025-06-18"
    mcp_tool_origin: str = "http://127.0.0.1:8001"

    def model_post_init(self, __context: object) -> None:
        """在未显式提供能力级 API key 时，根据当前 provider 回退到兼容变量。"""
        if not self.embedding_api_key:
            self.embedding_api_key = self._resolve_provider_api_key(self.embedding_provider)
        if not self.chat_api_key:
            self.chat_api_key = self._resolve_provider_api_key(self.chat_provider)

    def _resolve_provider_api_key(self, provider: str) -> str:
        """按 provider 选择兼容环境变量，避免把错误的 key 发给上游。"""
        normalized = provider.strip().lower()
        if "dashscope" in normalized or "aliyun" in normalized or "bailian" in normalized:
            return self.dashscope_api_key or self.openai_api_key
        if "deepseek" in normalized:
            return self.deepseek_api_key or self.openai_api_key
        return self.openai_api_key or self.dashscope_api_key or self.deepseek_api_key


@lru_cache
def get_settings() -> Settings:
    """缓存 Settings，避免每次依赖注入都重复解析环境变量。"""
    return Settings()
