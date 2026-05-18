from __future__ import annotations

from datetime import datetime, timezone

from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str
    time: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
