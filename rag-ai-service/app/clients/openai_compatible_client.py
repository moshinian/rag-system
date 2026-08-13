from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any

import httpx
from tenacity import AsyncRetrying, retry_if_exception, stop_after_attempt, wait_fixed

from app.core.config import Settings
from app.core.exceptions import ProviderError

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class ProviderTarget:
    """描述一次上游 provider 调用所需的目标信息。"""
    capability: str
    provider: str
    base_url: str
    api_key: str
    default_model: str
    path: str
    read_timeout_ms: int | None = None
    retry_attempts: int = 3


class OpenAiCompatibleProviderClient:
    """最小 OpenAI 兼容 HTTP 客户端，负责超时、重试和错误映射。"""

    def __init__(self, settings: Settings, *, read_timeout_ms: int | None = None) -> None:
        """根据配置初始化复用型 HTTP 客户端。"""
        self.settings = settings
        resolved_read_timeout_ms = read_timeout_ms or settings.http_read_timeout_ms
        timeout = httpx.Timeout(
            connect=settings.http_connect_timeout_ms / 1000.0,
            read=resolved_read_timeout_ms / 1000.0,
            write=resolved_read_timeout_ms / 1000.0,
            pool=settings.http_connect_timeout_ms / 1000.0,
        )
        self.http_client = httpx.AsyncClient(
            timeout=timeout,
            limits=httpx.Limits(
                max_connections=max(1, settings.http_max_connections),
                max_keepalive_connections=max(0, settings.http_max_keepalive_connections),
            ),
        )

    async def post_json(self, target: ProviderTarget, payload: dict[str, Any], request_id: str) -> dict[str, Any]:
        """对外暴露统一 JSON POST 入口。"""
        attempts = max(1, target.retry_attempts)
        retrying = AsyncRetrying(
            reraise=True,
            stop=stop_after_attempt(attempts),
            wait=wait_fixed(0.2),
            retry=retry_if_exception(lambda exc: _is_retryable_exception(exc)),
        )
        return await retrying(self._post_json, target, payload, request_id)

    async def _post_json(self, target: ProviderTarget, payload: dict[str, Any], request_id: str) -> dict[str, Any]:
        """向上游 provider 发起请求，并把常见失败收口成统一异常。"""
        url = _join_url(target.base_url, target.path)
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "X-Request-Id": request_id,
        }
        # 是否附带鉴权头由配置决定，避免把具体供应商约束写死在调用层。
        if target.api_key:
            headers["Authorization"] = "Bearer " + target.api_key

        try:
            if target.read_timeout_ms is not None:
                timeout = httpx.Timeout(
                    connect=self.settings.http_connect_timeout_ms / 1000.0,
                    read=target.read_timeout_ms / 1000.0,
                    write=target.read_timeout_ms / 1000.0,
                    pool=self.settings.http_connect_timeout_ms / 1000.0,
                )
                response = await self.http_client.post(url, headers=headers, json=payload, timeout=timeout)
            else:
                response = await self.http_client.post(url, headers=headers, json=payload)
        except httpx.TimeoutException as exc:
            raise ProviderError(
                message=f"{target.capability} upstream timeout",
                error_type="timeout",
                code="upstream_timeout",
                status_code=504,
            ) from exc
        except httpx.HTTPError as exc:
            raise ProviderError(
                message=f"{target.capability} upstream network error",
                error_type="provider_error",
                code="upstream_network_error",
                status_code=502,
            ) from exc

        if response.status_code >= 400:
            # 尽量保留上游 error.message，便于 Java 和前端看到更可读的失败原因。
            error_body = _safe_json(response)
            message = _extract_error_message(error_body) or response.text
            status_code = response.status_code
            # 429 和上游 5xx 视为可重试错误，交给 tenacity 进行有限重试。
            if status_code in (429, 500, 502, 503, 504):
                raise ProviderError(
                    message=message or f"{target.capability} upstream error",
                    error_type="provider_error",
                    code=f"upstream_{status_code}",
                    status_code=502 if status_code >= 500 else 429,
                )
            raise ProviderError(
                message=message or f"{target.capability} upstream request rejected",
                error_type="provider_error",
                code=f"upstream_{status_code}",
                status_code=status_code,
            )

        return response.json()

    async def aclose(self) -> None:
        """关闭共享连接池，供 FastAPI 生命周期与测试显式释放资源。"""
        await self.http_client.aclose()


def _join_url(base_url: str, path: str) -> str:
    """拼接 base_url 和 path，避免出现重复或缺失斜杠。"""
    if base_url.endswith("/") and path.startswith("/"):
        return base_url[:-1] + path
    if not base_url.endswith("/") and not path.startswith("/"):
        return base_url + "/" + path
    return base_url + path


def _safe_json(response: httpx.Response) -> dict[str, Any]:
    """尽量解析错误响应 JSON，失败时退化为空字典。"""
    try:
        return response.json()
    except ValueError:
        return {}


def _extract_error_message(payload: dict[str, Any]) -> str | None:
    """从 OpenAI 兼容或 DashScope 错误结构中提取可展示的 message。"""
    error = payload.get("error")
    if isinstance(error, dict):
        message = error.get("message")
        if isinstance(message, str) and message.strip():
            return message.strip()
    message = payload.get("message")
    if isinstance(message, str) and message.strip():
        return message.strip()
    return None


def _is_retryable_exception(exc: BaseException) -> bool:
    """只对网关定义为临时性的错误做有限重试。"""
    if not isinstance(exc, ProviderError):
        return False
    return exc.status_code in (429, 502, 504)
