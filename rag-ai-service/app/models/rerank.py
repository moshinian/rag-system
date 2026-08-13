from __future__ import annotations

from pydantic import BaseModel, Field, model_validator


class RerankRequest(BaseModel):
    """统一的文本重排序请求体。"""

    model: str | None = None
    query: str = Field(min_length=1, max_length=4000)
    documents: list[str] = Field(min_length=1, max_length=50)
    top_n: int = Field(ge=1, le=50)
    instruct: str | None = Field(default=None, max_length=1000)

    @model_validator(mode="after")
    def validate_documents_and_top_n(self) -> "RerankRequest":
        """拒绝空文档，并保证 top_n 不超过候选数量。"""
        if any(not document.strip() for document in self.documents):
            raise ValueError("documents must contain only non-empty strings")
        if self.top_n > len(self.documents):
            raise ValueError("top_n must be less than or equal to documents length")
        return self


class RerankResult(BaseModel):
    """单条重排序结果，通过 index 映射回输入文档。"""

    index: int = Field(ge=0)
    relevance_score: float = Field(ge=0, le=1)


class RerankUsage(BaseModel):
    """重排序接口的最小 token 使用量。"""

    total_tokens: int = 0


class RerankResponse(BaseModel):
    """统一的文本重排序响应体。"""

    object: str = "list"
    model: str
    results: list[RerankResult]
    usage: RerankUsage = Field(default_factory=RerankUsage)
