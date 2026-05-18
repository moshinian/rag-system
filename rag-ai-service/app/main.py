from fastapi import FastAPI

from app.api.routes import router
from app.core.exception_handlers import register_exception_handlers
from app.core.middleware import RequestIdMiddleware


def create_app() -> FastAPI:
    app = FastAPI(title="rag-ai-service", version="0.1.0")
    app.add_middleware(RequestIdMiddleware)
    register_exception_handlers(app)
    app.include_router(router)
    return app


app = create_app()
