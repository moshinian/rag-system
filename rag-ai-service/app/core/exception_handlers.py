from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.encoders import jsonable_encoder
from fastapi.responses import JSONResponse

from app.core.exceptions import ProviderError


def register_exception_handlers(app: FastAPI) -> None:
    """注册网关统一异常处理器。"""

    @app.exception_handler(ProviderError)
    async def handle_provider_error(_: Request, exc: ProviderError) -> JSONResponse:
        """把上游 provider 异常映射为统一错误结构。"""
        return JSONResponse(
            status_code=exc.status_code,
            content={
                "error": {
                    "message": exc.message,
                    "type": exc.error_type,
                    "code": exc.code,
                }
            },
        )

    @app.exception_handler(RequestValidationError)
    async def handle_validation_error(_: Request, exc: RequestValidationError) -> JSONResponse:
        """把 Pydantic 校验错误收口成稳定的 422 返回体。"""
        return JSONResponse(
            status_code=422,
            content={
                "error": {
                    "message": "Request validation failed",
                    "type": "validation_error",
                    "code": "request_validation_failed",
                    "details": jsonable_encoder(exc.errors()),
                }
            },
        )
