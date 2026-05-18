from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any

import httpx
from tenacity import retry, retry_if_exception, stop_after_attempt, wait_fixed

from app.core.config import Settings
from app.core.exceptions import ProviderError

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class ProviderTarget:
    capability: str
    provider: str
    base_url: str
    api_key: str
    default_model: str
    path: str


class OpenAiCompatibleProviderClient:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        timeout = httpx.Timeout(
            connect=settings.http_connect_timeout_ms / 1000.0,
            read=settings.http_read_timeout_ms / 1000.0,
            write=settings.http_read_timeout_ms / 1000.0,
            pool=settings.http_connect_timeout_ms / 1000.0,
        )
        self.http_client = httpx.Client(timeout=timeout)

    def post_json(self, target: ProviderTarget, payload: dict[str, Any], request_id: str) -> dict[str, Any]:
        return self._post_json(target, payload, request_id)

    @retry(
        reraise=True,
        stop=stop_after_attempt(3),
        wait=wait_fixed(0.2),
        retry=retry_if_exception(lambda exc: _is_retryable_exception(exc)),
    )
    def _post_json(self, target: ProviderTarget, payload: dict[str, Any], request_id: str) -> dict[str, Any]:
        url = _join_url(target.base_url, target.path)
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "X-Request-Id": request_id,
        }
        if target.api_key:
            headers["Authorization"] = "Bearer " + target.api_key

        try:
            response = self.http_client.post(url, headers=headers, json=payload)
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
            error_body = _safe_json(response)
            message = _extract_error_message(error_body) or response.text
            status_code = response.status_code
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
                status_code=400,
            )

        return response.json()


def _join_url(base_url: str, path: str) -> str:
    if base_url.endswith("/") and path.startswith("/"):
        return base_url[:-1] + path
    if not base_url.endswith("/") and not path.startswith("/"):
        return base_url + "/" + path
    return base_url + path


def _safe_json(response: httpx.Response) -> dict[str, Any]:
    try:
        return response.json()
    except ValueError:
        return {}


def _extract_error_message(payload: dict[str, Any]) -> str | None:
    error = payload.get("error")
    if isinstance(error, dict):
        message = error.get("message")
        if isinstance(message, str) and message.strip():
            return message.strip()
    return None


def _is_retryable_exception(exc: BaseException) -> bool:
    if not isinstance(exc, ProviderError):
        return False
    return exc.status_code in (429, 502, 504)
