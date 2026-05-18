from __future__ import annotations

import logging
import time
from functools import lru_cache
from typing import Any

from app.clients.openai_compatible_client import OpenAiCompatibleProviderClient, ProviderTarget
from app.core.config import Settings, get_settings
from app.models.chat import (
    ChatChoice,
    ChatChoiceMessage,
    ChatCompletionRequest,
    ChatCompletionResponse,
    Usage,
)
from app.models.embedding import EmbeddingData, EmbeddingRequest, EmbeddingResponse, EmbeddingUsage

log = logging.getLogger(__name__)


class GatewayService:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.provider_client = OpenAiCompatibleProviderClient(settings)

    def create_embeddings(self, payload: EmbeddingRequest, request_id: str) -> EmbeddingResponse:
        started_at = time.perf_counter()
        inputs = [payload.input] if isinstance(payload.input, str) else payload.input
        model = payload.model or self.settings.embedding_default_model
        upstream_payload = {"model": model, "input": inputs if len(inputs) > 1 else inputs[0]}
        target = ProviderTarget(
            capability="embedding",
            provider=self.settings.embedding_provider,
            base_url=self.settings.embedding_base_url,
            api_key=self.settings.embedding_api_key,
            default_model=self.settings.embedding_default_model,
            path=self.settings.embedding_path,
        )
        upstream = self.provider_client.post_json(target, upstream_payload, request_id)
        usage = upstream.get("usage") or {}
        response = EmbeddingResponse(
            data=[
                EmbeddingData(
                    index=item.get("index", index),
                    embedding=item.get("embedding", []),
                )
                for index, item in enumerate(upstream.get("data", []))
            ],
            model=upstream.get("model", model),
            usage=EmbeddingUsage(
                prompt_tokens=_as_int(usage.get("prompt_tokens")),
                total_tokens=_as_int(usage.get("total_tokens")),
            ),
        )
        self._log_call(
            capability="embedding",
            request_id=request_id,
            provider=target.provider,
            model=response.model,
            latency_ms=max(1, int((time.perf_counter() - started_at) * 1000)),
            input_count=len(inputs),
            usage={
                "prompt_tokens": response.usage.prompt_tokens,
                "total_tokens": response.usage.total_tokens,
            },
        )
        return response

    def create_chat_completion(self, payload: ChatCompletionRequest, request_id: str) -> ChatCompletionResponse:
        started_at = time.perf_counter()
        model = payload.model or self.settings.chat_default_model
        upstream_payload = payload.model_dump(exclude_none=True)
        upstream_payload["model"] = model
        target = ProviderTarget(
            capability="chat",
            provider=self.settings.chat_provider,
            base_url=self.settings.chat_base_url,
            api_key=self.settings.chat_api_key,
            default_model=self.settings.chat_default_model,
            path=self.settings.chat_path,
        )
        upstream = self.provider_client.post_json(target, upstream_payload, request_id)
        usage = upstream.get("usage") or {}
        response = ChatCompletionResponse(
            id=upstream.get("id", "chatcmpl-rag-ai-service"),
            object=upstream.get("object", "chat.completion"),
            created=_as_int(upstream.get("created")) or int(time.time()),
            model=upstream.get("model", model),
            choices=[
                ChatChoice(
                    index=item.get("index", index),
                    finish_reason=item.get("finish_reason"),
                    message=ChatChoiceMessage(
                        role=((item.get("message") or {}).get("role") or "assistant"),
                        content=((item.get("message") or {}).get("content") or ""),
                    ),
                )
                for index, item in enumerate(upstream.get("choices", []))
            ],
            usage=Usage(
                prompt_tokens=_as_int(usage.get("prompt_tokens")),
                completion_tokens=_as_int(usage.get("completion_tokens")),
                total_tokens=_as_int(usage.get("total_tokens")),
            ),
        )
        self._log_call(
            capability="chat",
            request_id=request_id,
            provider=target.provider,
            model=response.model,
            latency_ms=max(1, int((time.perf_counter() - started_at) * 1000)),
            message_count=len(payload.messages),
            usage={
                "prompt_tokens": response.usage.prompt_tokens,
                "completion_tokens": response.usage.completion_tokens,
                "total_tokens": response.usage.total_tokens,
            },
        )
        return response

    def _log_call(self, capability: str, request_id: str, provider: str, model: str, latency_ms: int, **extra: Any) -> None:
        fields: dict[str, Any] = {
            "event": "ai.gateway.request.completed",
            "capability": capability,
            "request_id": request_id,
            "provider": provider,
            "model": model,
            "latency_ms": latency_ms,
        }
        fields.update(extra)
        log.info(" ".join(f"{key}={_quote(value)}" for key, value in fields.items() if value is not None))


@lru_cache
def get_gateway_service() -> GatewayService:
    return GatewayService(get_settings())


def _quote(value: Any) -> str:
    text = str(value)
    if not text or any(char.isspace() for char in text):
        return '"' + text.replace("\\", "\\\\").replace('"', '\\"') + '"'
    return text


def _as_int(value: Any) -> int:
    if isinstance(value, int):
        return value
    if isinstance(value, float):
        return int(value)
    return 0
