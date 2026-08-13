import asyncio

import httpx

from app.api.routes import resolve_gateway_service
from app.clients.openai_compatible_client import OpenAiCompatibleProviderClient, ProviderTarget
from app.core.config import get_settings
from app.core.exceptions import ProviderError
from app.main import create_app
from app.models.rerank import RerankRequest
from app.services.gateway_service import GatewayService


class StubGatewayService:
    async def create_embeddings(self, payload, request_id):
        return {
            "object": "list",
            "data": [
                {"object": "embedding", "index": 0, "embedding": [0.1, 0.2]},
            ],
            "model": payload.model or "text-embedding-v4",
            "usage": {"prompt_tokens": 3, "total_tokens": 3},
        }

    async def create_chat_completion(self, payload, request_id):
        return {
            "id": "chatcmpl-test",
            "object": "chat.completion",
            "created": 123,
            "model": payload.model or get_settings().chat_default_model,
            "choices": [
                {
                    "index": 0,
                    "message": {"role": "assistant", "content": "pong"},
                    "finish_reason": "stop",
                }
            ],
            "usage": {"prompt_tokens": 4, "completion_tokens": 1, "total_tokens": 5},
        }

    async def create_rerank(self, payload, request_id):
        return {
            "object": "list",
            "model": payload.model or "qwen3-rerank",
            "results": [
                {"index": 1, "relevance_score": 0.91},
                {"index": 0, "relevance_score": 0.42},
            ][: payload.top_n],
            "usage": {"total_tokens": 17},
        }


def build_app():
    app = create_app()

    async def override_gateway_service():
        return StubGatewayService()

    app.dependency_overrides[resolve_gateway_service] = override_gateway_service
    return app


async def request(app, method: str, url: str, **kwargs):
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
        sender = getattr(client, method.lower())
        response = await sender(url, **kwargs)
        await response.aread()
        return response


def test_health_returns_up_without_provider_call():
    app = build_app()
    settings = get_settings()
    response = asyncio.run(request(app, "GET", "/health"))

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "UP"
    assert body["service"] == settings.service_name
    assert body["embedding_provider"] == settings.embedding_provider
    assert body["embedding_default_model"] == settings.embedding_default_model
    assert body["chat_provider"] == settings.chat_provider
    assert body["chat_default_model"] == settings.chat_default_model
    assert body["rerank_provider"] == settings.rerank_provider
    assert body["rerank_default_model"] == settings.rerank_default_model


def test_embeddings_returns_openai_compatible_payload_and_request_id():
    app = build_app()
    response = asyncio.run(
        request(
            app,
            "POST",
            "/v1/embeddings",
            headers={"X-Request-Id": "REQ-123"},
            json={"model": "text-embedding-v4", "input": ["hello"]},
        )
    )

    assert response.status_code == 200
    assert response.headers["X-Request-Id"] == "REQ-123"
    body = response.json()
    assert body["model"] == "text-embedding-v4"
    assert body["data"][0]["embedding"] == [0.1, 0.2]
    assert body["usage"]["total_tokens"] == 3


def test_chat_returns_openai_compatible_payload():
    app = build_app()
    model = "test-chat-model"
    response = asyncio.run(
        request(
            app,
            "POST",
            "/v1/chat/completions",
            json={
                "model": model,
                "messages": [
                    {"role": "system", "content": "You are a bot."},
                    {"role": "user", "content": "ping"},
                ],
                "temperature": 0.2,
                "max_tokens": 16,
            },
        )
    )

    assert response.status_code == 200
    body = response.json()
    assert body["model"] == model
    assert body["choices"][0]["message"]["content"] == "pong"
    assert body["usage"]["total_tokens"] == 5


def test_rerank_returns_indexed_scores_and_request_id():
    app = build_app()
    response = asyncio.run(
        request(
            app,
            "POST",
            "/v1/rerank",
            headers={"X-Request-Id": "REQ-RERANK-1"},
            json={
                "model": "qwen3-rerank",
                "query": "如何处理结算异常",
                "documents": ["无关内容", "先检查结算任务状态"],
                "top_n": 2,
            },
        )
    )

    assert response.status_code == 200
    assert response.headers["X-Request-Id"] == "REQ-RERANK-1"
    assert response.json()["results"][0] == {"index": 1, "relevance_score": 0.91}


def test_rerank_rejects_top_n_greater_than_candidate_count():
    app = build_app()
    response = asyncio.run(
        request(
            app,
            "POST",
            "/v1/rerank",
            json={"query": "query", "documents": ["only one"], "top_n": 2},
        )
    )

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "request_validation_failed"


def test_gateway_maps_canonical_rerank_request_to_qwen_payload():
    service = GatewayService(get_settings())
    captured = {}

    async def fake_post(target, payload, request_id):
        captured["target"] = target
        captured["payload"] = payload
        captured["request_id"] = request_id
        return {
            "object": "list",
            "model": "qwen3-rerank",
            "results": [{"index": 1, "relevance_score": 0.95}],
            "usage": {"total_tokens": 21},
        }

    service.provider_client.post_json = fake_post
    response = asyncio.run(
        service.create_rerank(
            RerankRequest(
                query="如何排查结算异常",
                documents=["无关内容", "检查任务状态"],
                top_n=1,
                instruct=" rank answer evidence ",
            ),
            "REQ-RERANK-MAP",
        )
    )

    assert captured["payload"] == {
        "model": "qwen3-rerank",
        "input": {
            "query": "如何排查结算异常",
            "documents": ["无关内容", "检查任务状态"],
        },
        "parameters": {
            "top_n": 1,
            "instruct": "rank answer evidence",
        },
    }
    assert captured["target"].read_timeout_ms == 8000
    assert captured["target"].retry_attempts == 2
    assert captured["request_id"] == "REQ-RERANK-MAP"
    assert response.results[0].index == 1
    assert response.usage.total_tokens == 21


def test_gateway_supports_qwen3_flat_rerank_endpoint_format():
    from app.core.config import Settings

    service = GatewayService(Settings(rerank_request_format="qwen3-flat"))
    payload = service._build_rerank_upstream_payload(
        RerankRequest(query="query", documents=["doc"], top_n=1),
        "qwen3-rerank",
    )

    assert payload == {
        "model": "qwen3-rerank",
        "query": "query",
        "documents": ["doc"],
        "top_n": 1,
    }


def test_gateway_rejects_malformed_rerank_provider_response():
    service = GatewayService(get_settings())
    async def malformed_response(*_):
        return {"model": "qwen3-rerank"}

    service.provider_client.post_json = malformed_response

    try:
        asyncio.run(
            service.create_rerank(
                RerankRequest(query="query", documents=["document"], top_n=1),
                "REQ-RERANK-BAD",
            )
        )
    except ProviderError as exc:
        assert exc.status_code == 502
        assert exc.code == "invalid_upstream_response"
    else:
        raise AssertionError("expected ProviderError for malformed rerank response")


def test_gateway_accepts_dashscope_output_wrapped_rerank_response():
    service = GatewayService(get_settings())
    async def wrapped_response(*_):
        return {
            "output": {
                "results": [
                    {"index": 0, "relevance_score": 0.88, "document": {"text": "doc"}},
                ]
            },
            "usage": {"total_tokens": 9},
        }

    service.provider_client.post_json = wrapped_response

    response = asyncio.run(
        service.create_rerank(
            RerankRequest(query="query", documents=["doc"], top_n=1),
            "REQ-RERANK-WRAPPED",
        )
    )

    assert response.model == "qwen3-rerank"
    assert response.results[0].relevance_score == 0.88


def test_validation_error_returns_uniform_error_shape():
    app = build_app()
    response = asyncio.run(request(app, "POST", "/v1/embeddings", json={"input": []}))

    assert response.status_code == 422
    body = response.json()
    assert body["error"]["type"] == "validation_error"


def test_settings_accept_provider_specific_api_key_env_vars(monkeypatch):
    monkeypatch.setenv("DASHSCOPE_API_KEY", "dashscope-test-key")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "deepseek-test-key")
    monkeypatch.delenv("EMBEDDING_API_KEY", raising=False)
    monkeypatch.delenv("CHAT_API_KEY", raising=False)
    monkeypatch.delenv("RERANK_API_KEY", raising=False)

    from app.core.config import Settings

    settings = Settings(chat_provider="aliyun-bailian-openai-compatible")

    assert settings.embedding_api_key == "dashscope-test-key"
    assert settings.chat_api_key == "dashscope-test-key"
    assert settings.rerank_api_key == "dashscope-test-key"


def test_settings_choose_deepseek_key_for_deepseek_chat_provider(monkeypatch):
    monkeypatch.setenv("DASHSCOPE_API_KEY", "dashscope-test-key")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "deepseek-test-key")
    monkeypatch.delenv("CHAT_API_KEY", raising=False)

    from app.core.config import Settings

    settings = Settings(chat_provider="deepseek-openai-compatible")

    assert settings.chat_api_key == "deepseek-test-key"


def test_upstream_401_is_preserved_in_gateway_response():
    app = create_app()

    class UnauthorizedGatewayService:
        async def create_embeddings(self, payload, request_id):
            raise ProviderError(
                message="missing provider api key",
                error_type="provider_error",
                code="upstream_401",
                status_code=401,
            )

    async def override_gateway_service():
        return UnauthorizedGatewayService()

    app.dependency_overrides[resolve_gateway_service] = override_gateway_service
    response = asyncio.run(request(app, "POST", "/v1/embeddings", json={"input": "hello"}))

    assert response.status_code == 401
    assert response.json()["error"]["code"] == "upstream_401"


def test_provider_client_preserves_non_retryable_upstream_401_status():
    settings = get_settings()
    client = OpenAiCompatibleProviderClient(settings)
    target = ProviderTarget(
        capability="chat",
        provider="test-provider",
        base_url="https://example.com",
        api_key="",
        default_model="test-model",
        path="/chat/completions",
    )

    async def fake_post(*args, **kwargs):
        request = httpx.Request("POST", "https://example.com/chat/completions")
        return httpx.Response(
            401,
            request=request,
            json={"error": {"message": "missing api key"}},
        )

    client.http_client.post = fake_post

    try:
        asyncio.run(client.post_json(target, {"model": "test-model", "messages": []}, "REQ-401"))
    except ProviderError as exc:
        assert exc.status_code == 401
        assert exc.code == "upstream_401"
        assert exc.message == "missing api key"
    else:
        raise AssertionError("expected ProviderError for upstream 401")


def test_provider_client_extracts_dashscope_top_level_error_message():
    settings = get_settings()
    client = OpenAiCompatibleProviderClient(settings)
    target = ProviderTarget(
        capability="rerank",
        provider="aliyun-bailian",
        base_url="https://example.com",
        api_key="",
        default_model="qwen3-rerank",
        path="/rerank",
        retry_attempts=1,
    )

    async def fake_post(*args, **kwargs):
        request = httpx.Request("POST", "https://example.com/rerank")
        return httpx.Response(
            400,
            request=request,
            json={"code": "InvalidParameter", "message": "top_n is invalid"},
        )

    client.http_client.post = fake_post

    try:
        asyncio.run(client.post_json(target, {"model": "qwen3-rerank"}, "REQ-RERANK-400"))
    except ProviderError as exc:
        assert exc.status_code == 400
        assert exc.message == "top_n is invalid"
    else:
        raise AssertionError("expected ProviderError for DashScope error response")
