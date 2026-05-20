from fastapi import APIRouter, Depends, Request, Response

from app.core.config import Settings, get_settings
from app.models.chat import ChatCompletionRequest, ChatCompletionResponse
from app.models.embedding import EmbeddingRequest, EmbeddingResponse
from app.models.health import HealthResponse
from app.services.gateway_service import GatewayService, get_gateway_service

router = APIRouter()


@router.get("/health", response_model=HealthResponse)
def health(settings: Settings = Depends(get_settings)) -> HealthResponse:
    return HealthResponse(
        status="UP",
        service=settings.service_name,
        version=settings.service_version,
        embedding_provider=settings.embedding_provider,
        embedding_default_model=settings.embedding_default_model,
        chat_provider=settings.chat_provider,
        chat_default_model=settings.chat_default_model,
    )


@router.post("/v1/embeddings", response_model=EmbeddingResponse)
def create_embeddings(
    payload: EmbeddingRequest,
    request: Request,
    response: Response,
    gateway_service: GatewayService = Depends(get_gateway_service),
) -> EmbeddingResponse:
    request_id = request.state.request_id
    response.headers["X-Request-Id"] = request_id
    return gateway_service.create_embeddings(payload, request_id)


@router.post("/v1/chat/completions", response_model=ChatCompletionResponse)
def create_chat_completion(
    payload: ChatCompletionRequest,
    request: Request,
    response: Response,
    gateway_service: GatewayService = Depends(get_gateway_service),
) -> ChatCompletionResponse:
    request_id = request.state.request_id
    response.headers["X-Request-Id"] = request_id
    return gateway_service.create_chat_completion(payload, request_id)
