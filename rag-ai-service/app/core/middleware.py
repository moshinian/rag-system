from __future__ import annotations

import time
import uuid

from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint


class RequestIdMiddleware(BaseHTTPMiddleware):
    """为每个请求补齐 requestId，并记录响应耗时。"""

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        """在请求进入路由前后分别注入追踪信息。"""
        request_id = request.headers.get("X-Request-Id") or f"REQ-{uuid.uuid4().hex}"
        request.state.request_id = request_id
        started_at = time.perf_counter()
        response = await call_next(request)
        response.headers["X-Request-Id"] = request_id
        # 响应耗时统一取毫秒整数，至少为 1，避免出现 0ms 的误导性展示。
        response.headers["X-Response-Time-Ms"] = str(max(1, int((time.perf_counter() - started_at) * 1000)))
        return response
