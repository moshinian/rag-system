from __future__ import annotations

from datetime import datetime, timezone

from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    """健康检查响应体。"""

    status: str
    service: str
    version: str
    embedding_provider: str
    embedding_default_model: str
    chat_provider: str
    chat_default_model: str
    rerank_provider: str
    rerank_default_model: str
    time: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
