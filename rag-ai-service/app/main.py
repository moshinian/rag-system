from fastapi import FastAPI

from app.api.routes import router
from app.core.exception_handlers import register_exception_handlers
from app.core.middleware import RequestIdMiddleware


def create_app() -> FastAPI:
    """创建并装配 FastAPI 应用。"""
    app = FastAPI(title="rag-ai-service", version="0.1.0")
    # 所有请求统一补齐 requestId 和耗时响应头，便于 Java 主链路透传追踪。
    app.add_middleware(RequestIdMiddleware)
    # 统一错误结构，避免不同异常类型返回体漂移。
    register_exception_handlers(app)
    # 收口对外公开的 AI Gateway 路由。
    app.include_router(router)
    return app


# 模块级 app 供 Uvicorn / Gunicorn 直接发现并启动。
app = create_app()
