from fastapi.testclient import TestClient

from app.main import create_app
from app.services.gateway_service import get_gateway_service


class StubGatewayService:
    def create_embeddings(self, payload, request_id):
        return {
            "object": "list",
            "data": [
                {"object": "embedding", "index": 0, "embedding": [0.1, 0.2]},
            ],
            "model": payload.model or "text-embedding-v4",
            "usage": {"prompt_tokens": 3, "total_tokens": 3},
        }

    def create_chat_completion(self, payload, request_id):
        return {
            "id": "chatcmpl-test",
            "object": "chat.completion",
            "created": 123,
            "model": payload.model or "deepseek-v4-pro",
            "choices": [
                {
                    "index": 0,
                    "message": {"role": "assistant", "content": "pong"},
                    "finish_reason": "stop",
                }
            ],
            "usage": {"prompt_tokens": 4, "completion_tokens": 1, "total_tokens": 5},
        }


def build_client() -> TestClient:
    app = create_app()
    app.dependency_overrides[get_gateway_service] = lambda: StubGatewayService()
    return TestClient(app)


def test_health_returns_up_without_provider_call():
    client = build_client()

    response = client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "UP"
    assert body["service"] == "rag-ai-service"


def test_embeddings_returns_openai_compatible_payload_and_request_id():
    client = build_client()

    response = client.post(
        "/v1/embeddings",
        headers={"X-Request-Id": "REQ-123"},
        json={"model": "text-embedding-v4", "input": ["hello"]},
    )

    assert response.status_code == 200
    assert response.headers["X-Request-Id"] == "REQ-123"
    body = response.json()
    assert body["model"] == "text-embedding-v4"
    assert body["data"][0]["embedding"] == [0.1, 0.2]
    assert body["usage"]["total_tokens"] == 3


def test_chat_returns_openai_compatible_payload():
    client = build_client()

    response = client.post(
        "/v1/chat/completions",
        json={
            "model": "deepseek-v4-pro",
            "messages": [
                {"role": "system", "content": "You are a bot."},
                {"role": "user", "content": "ping"},
            ],
            "temperature": 0.2,
            "max_tokens": 16,
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["choices"][0]["message"]["content"] == "pong"
    assert body["usage"]["total_tokens"] == 5


def test_validation_error_returns_uniform_error_shape():
    client = build_client()

    response = client.post("/v1/embeddings", json={"input": []})

    assert response.status_code == 422
    body = response.json()
    assert body["error"]["type"] == "validation_error"
