from __future__ import annotations

from typing import Union

from pydantic import BaseModel, Field, field_validator


class EmbeddingRequest(BaseModel):
    model: str | None = None
    input: Union[str, list[str]]

    @field_validator("input")
    @classmethod
    def validate_input(cls, value: Union[str, list[str]]) -> Union[str, list[str]]:
        if isinstance(value, str) and value.strip():
            return value
        if isinstance(value, list) and value and all(isinstance(item, str) and item.strip() for item in value):
            return value
        raise ValueError("input must be a non-empty string or string array")


class EmbeddingData(BaseModel):
    object: str = "embedding"
    index: int
    embedding: list[float]


class EmbeddingUsage(BaseModel):
    prompt_tokens: int = 0
    total_tokens: int = 0


class EmbeddingResponse(BaseModel):
    object: str = "list"
    data: list[EmbeddingData]
    model: str
    usage: EmbeddingUsage = Field(default_factory=EmbeddingUsage)
