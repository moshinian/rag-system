from __future__ import annotations

import time
from typing import Literal

from pydantic import BaseModel, Field


class ChatMessage(BaseModel):
    """单条对话消息。"""

    role: Literal["system", "user", "assistant"]
    content: str = Field(min_length=1)


class ChatCompletionRequest(BaseModel):
    """chat completion 请求体。"""

    model: str | None = None
    messages: list[ChatMessage] = Field(min_length=1)
    temperature: float | None = None
    max_tokens: int | None = None


class ChatChoiceMessage(BaseModel):
    """返回给调用方的单条回答消息。"""

    role: str
    content: str


class ChatChoice(BaseModel):
    """单个候选回答。"""

    index: int
    message: ChatChoiceMessage
    finish_reason: str | None = None


class Usage(BaseModel):
    """chat 接口的最小 token 使用量信息。"""

    prompt_tokens: int = 0
    completion_tokens: int = 0
    total_tokens: int = 0


class ChatCompletionResponse(BaseModel):
    """chat completion 响应体。"""

    id: str = "chatcmpl-rag-ai-service"
    object: str = "chat.completion"
    created: int = Field(default_factory=lambda: int(time.time()))
    model: str
    choices: list[ChatChoice]
    usage: Usage = Field(default_factory=Usage)
