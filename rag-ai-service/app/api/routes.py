from fastapi import APIRouter, Depends, Request, Response

from app.agent.runtime import AgentRuntime, get_agent_runtime
from app.agent.state import AgentRuntimeRequest, AgentRuntimeResponse
from app.core.config import Settings, get_settings
from app.models.chat import ChatCompletionRequest, ChatCompletionResponse
from app.models.embedding import EmbeddingRequest, EmbeddingResponse
from app.models.health import HealthResponse
from app.services.gateway_service import GatewayService, get_gateway_service

router = APIRouter()


async def resolve_settings() -> Settings:
    """为异步路由提供配置依赖。"""
    return get_settings()


async def resolve_gateway_service() -> GatewayService:
    """为异步路由提供网关服务依赖。"""
    return get_gateway_service()


@router.get("/health", response_model=HealthResponse)
async def health(settings: Settings = Depends(resolve_settings)) -> HealthResponse:
    """返回网关自身存活状态和当前生效的运行配置摘要。"""
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
async def create_embeddings(
    payload: EmbeddingRequest,
    request: Request,
    response: Response,
    gateway_service: GatewayService = Depends(resolve_gateway_service),
) -> EmbeddingResponse:
    """创建 embedding，并把 requestId 回传给调用方。"""
    request_id = request.state.request_id
    response.headers["X-Request-Id"] = request_id
    return await gateway_service.create_embeddings(payload, request_id)


@router.post("/v1/chat/completions", response_model=ChatCompletionResponse)
async def create_chat_completion(
    payload: ChatCompletionRequest,
    request: Request,
    response: Response,
    gateway_service: GatewayService = Depends(resolve_gateway_service),
) -> ChatCompletionResponse:
    """创建 chat completion，并把 requestId 回传给调用方。"""
    request_id = request.state.request_id
    response.headers["X-Request-Id"] = request_id
    return await gateway_service.create_chat_completion(payload, request_id)


@router.post("/v1/agent/runs", response_model=AgentRuntimeResponse)
def run_agent(
    payload: AgentRuntimeRequest,
    request: Request,
    response: Response,
    agent_runtime: AgentRuntime = Depends(get_agent_runtime),
) -> AgentRuntimeResponse:
    """执行一次 Agent Runtime，并透传 requestId 响应头。"""
    request_id = request.state.request_id
    response.headers["X-Request-Id"] = request_id
    return agent_runtime.run(payload)
