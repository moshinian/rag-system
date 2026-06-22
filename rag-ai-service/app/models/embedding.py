from __future__ import annotations

from typing import Union

from pydantic import BaseModel, Field, field_validator


class EmbeddingRequest(BaseModel):
    """embedding 请求体。"""

    model: str | None = None
    input: Union[str, list[str]]

    @field_validator("input")
    @classmethod
    def validate_input(cls, value: Union[str, list[str]]) -> Union[str, list[str]]:
        """保证 input 只能是非空字符串或非空字符串数组。"""
        if isinstance(value, str) and value.strip():
            return value
        if isinstance(value, list) and value and all(isinstance(item, str) and item.strip() for item in value):
            return value
        raise ValueError("input must be a non-empty string or string array")


class EmbeddingData(BaseModel):
    """单条 embedding 结果。"""

    object: str = "embedding"
    index: int
    embedding: list[float]


class EmbeddingUsage(BaseModel):
    """embedding 接口的最小 token 使用量信息。"""

    prompt_tokens: int = 0
    total_tokens: int = 0


class EmbeddingResponse(BaseModel):
    """embedding 响应体。"""

    object: str = "list"
    data: list[EmbeddingData]
    model: str
    usage: EmbeddingUsage = Field(default_factory=EmbeddingUsage)
