from __future__ import annotations

import logging
import time
from functools import lru_cache
from typing import Any

from app.clients.openai_compatible_client import OpenAiCompatibleProviderClient, ProviderTarget
from app.core.config import Settings, get_settings
from app.core.exceptions import ProviderError
from app.models.chat import (
    ChatChoice,
    ChatChoiceMessage,
    ChatCompletionRequest,
    ChatCompletionResponse,
    Usage,
)
from app.models.embedding import EmbeddingData, EmbeddingRequest, EmbeddingResponse, EmbeddingUsage
from app.models.rerank import RerankRequest, RerankResponse, RerankResult, RerankUsage

log = logging.getLogger(__name__)


class GatewayService:
    """编排向量化和聊天请求，并转换为网关统一响应模型。"""

    def __init__(self, settings: Settings) -> None:
        """创建服务并注入底层 provider 客户端。"""
        self.settings = settings
        self.provider_client = OpenAiCompatibleProviderClient(settings)

    async def create_embeddings(self, payload: EmbeddingRequest, request_id: str) -> EmbeddingResponse:
        """处理向量化请求，并把上游返回映射成标准响应。"""
        started_at = time.perf_counter()
        # OpenAI 兼容协议同时支持单字符串和字符串数组输入，这里统一归一化。
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
        upstream = await self.provider_client.post_json(target, upstream_payload, request_id)
        usage = upstream.get("usage") or {}
        # 对上游缺失字段做兜底，避免兼容实现存在轻微差异时直接打断主链路。
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

    async def create_chat_completion(self, payload: ChatCompletionRequest, request_id: str) -> ChatCompletionResponse:
        """处理聊天补全请求，并映射最小 OpenAI 兼容子集。"""
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
        upstream = await self.provider_client.post_json(target, upstream_payload, request_id)
        usage = upstream.get("usage") or {}
        # 候选结果和用量字段采用宽松解析，兼容不同上游实现的细小结构差异。
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

    async def create_rerank(self, payload: RerankRequest, request_id: str) -> RerankResponse:
        """调用文本排序模型，并映射为稳定的 index + score 契约。"""
        started_at = time.perf_counter()
        model = payload.model or self.settings.rerank_default_model
        upstream_payload = self._build_rerank_upstream_payload(payload, model)
        target = ProviderTarget(
            capability="rerank",
            provider=self.settings.rerank_provider,
            base_url=self.settings.rerank_base_url,
            api_key=self.settings.rerank_api_key,
            default_model=self.settings.rerank_default_model,
            path=self.settings.rerank_path,
            read_timeout_ms=self.settings.rerank_read_timeout_ms,
            retry_attempts=self.settings.rerank_retry_attempts,
        )
        upstream = await self.provider_client.post_json(target, upstream_payload, request_id)
        raw_results = upstream.get("results")
        if raw_results is None and isinstance(upstream.get("output"), dict):
            raw_results = upstream["output"].get("results")
        if not isinstance(raw_results, list):
            raise _invalid_rerank_response("rerank upstream response does not contain results")
        try:
            response = RerankResponse(
                model=upstream.get("model", model),
                results=[
                    RerankResult(
                        index=item.get("index"),
                        relevance_score=item.get("relevance_score"),
                    )
                    for item in raw_results
                ],
                usage=RerankUsage(total_tokens=_as_int((upstream.get("usage") or {}).get("total_tokens"))),
            )
        except (AttributeError, TypeError, ValueError) as exc:
            raise _invalid_rerank_response("rerank upstream response is invalid") from exc
        self._log_call(
            capability="rerank",
            request_id=request_id,
            provider=target.provider,
            model=response.model,
            latency_ms=max(1, int((time.perf_counter() - started_at) * 1000)),
            candidate_count=len(payload.documents),
            result_count=len(response.results),
            usage={"total_tokens": response.usage.total_tokens},
        )
        return response

    def _build_rerank_upstream_payload(self, payload: RerankRequest, model: str) -> dict[str, Any]:
        """按端点版本构造 DashScope rerank 请求，统一对外契约保持不变。"""
        instruct = payload.instruct.strip() if payload.instruct and payload.instruct.strip() else None
        if self.settings.rerank_request_format.strip().lower() == "qwen3-flat":
            upstream_payload: dict[str, Any] = {
                "model": model,
                "query": payload.query,
                "documents": payload.documents,
                "top_n": payload.top_n,
            }
            if instruct:
                upstream_payload["instruct"] = instruct
            return upstream_payload

        parameters: dict[str, Any] = {"top_n": payload.top_n}
        if instruct:
            parameters["instruct"] = instruct
        return {
            "model": model,
            "input": {
                "query": payload.query,
                "documents": payload.documents,
            },
            "parameters": parameters,
        }

    def _log_call(self, capability: str, request_id: str, provider: str, model: str, latency_ms: int, **extra: Any) -> None:
        """输出最小结构化日志，便于和 Java 主链路按 requestId 关联。"""
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
    """缓存 GatewayService，复用底层 HTTP 客户端。"""
    return GatewayService(get_settings())


def _quote(value: Any) -> str:
    """为日志字段生成简单的 key=value 安全文本。"""
    text = str(value)
    if not text or any(char.isspace() for char in text):
        return '"' + text.replace("\\", "\\\\").replace('"', '\\"') + '"'
    return text


def _as_int(value: Any) -> int:
    """把上游返回的数值宽松转换为 int。"""
    if isinstance(value, int):
        return value
    if isinstance(value, float):
        return int(value)
    return 0


def _invalid_rerank_response(message: str) -> ProviderError:
    """把不可消费的供应商响应映射为可降级的统一 502。"""
    return ProviderError(
        message=message,
        error_type="provider_error",
        code="invalid_upstream_response",
        status_code=502,
    )
