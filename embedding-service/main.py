import os
from typing import List

from fastapi import FastAPI
from fastapi import HTTPException
from pydantic import BaseModel, Field, field_validator
from sentence_transformers import SentenceTransformer


MODEL_NAME = os.getenv("EMBEDDING_MODEL_NAME", "BAAI/bge-small-zh-v1.5")
MODEL_PATH = os.getenv("EMBEDDING_MODEL_PATH", "").strip()
DEVICE = os.getenv("EMBEDDING_DEVICE", "cpu")
NORMALIZE = os.getenv("EMBEDDING_NORMALIZE", "true").lower() == "true"
LOCAL_FILES_ONLY = os.getenv("EMBEDDING_LOCAL_FILES_ONLY", "false").lower() == "true"
PORT = int(os.getenv("EMBEDDING_PORT", "8001"))

_model: SentenceTransformer | None = None
_model_error: str | None = None


def get_model() -> SentenceTransformer:
    global _model, _model_error
    if _model is None:
        model_source = MODEL_PATH if MODEL_PATH else MODEL_NAME
        try:
            _model = SentenceTransformer(
                model_source,
                device=DEVICE,
                local_files_only=LOCAL_FILES_ONLY,
            )
            _model_error = None
        except Exception as ex:
            _model_error = str(ex)
            raise
    return _model


class EmbeddingRequest(BaseModel):
    model: str = Field(default="bge-small-zh-v1.5")
    input: str | List[str]

    @field_validator("input")
    @classmethod
    def validate_input(cls, value: str | List[str]) -> str | List[str]:
        if isinstance(value, str):
            if not value.strip():
                raise ValueError("input must not be blank")
            return value

        if not value:
            raise ValueError("input list must not be empty")
        if any(not item or not item.strip() for item in value):
            raise ValueError("input list must not contain blank text")
        return value


class EmbeddingItem(BaseModel):
    object: str = "embedding"
    index: int
    embedding: List[float]


class EmbeddingUsage(BaseModel):
    prompt_tokens: int
    total_tokens: int


class EmbeddingResponse(BaseModel):
    object: str = "list"
    data: List[EmbeddingItem]
    model: str
    usage: EmbeddingUsage


def estimate_tokens(texts: List[str]) -> int:
    total_chars = sum(len(text) for text in texts)
    return max(1, total_chars // 2)


app = FastAPI(title="Local Embedding Service", version="0.1.0")


@app.get("/health")
def health() -> dict:
    model_loaded = _model is not None
    resolved_model_source = MODEL_PATH if MODEL_PATH else MODEL_NAME
    local_model_config_exists = bool(MODEL_PATH) and os.path.exists(os.path.join(MODEL_PATH, "config.json"))
    return {
        "status": "UP",
        "modelName": MODEL_NAME,
        "modelSource": resolved_model_source,
        "modelLoaded": model_loaded,
        "localModelConfigExists": local_model_config_exists,
        "modelError": _model_error,
        "device": DEVICE,
        "localFilesOnly": LOCAL_FILES_ONLY,
        "embeddingDimensions": _model.get_sentence_embedding_dimension() if _model else None,
        "normalizeEmbeddings": NORMALIZE,
        "port": PORT,
    }


@app.post("/v1/embeddings", response_model=EmbeddingResponse)
def create_embeddings(request: EmbeddingRequest) -> EmbeddingResponse:
    texts = [request.input] if isinstance(request.input, str) else request.input
    try:
        model = get_model()
    except Exception as ex:
        raise HTTPException(
            status_code=503,
            detail={
                "message": "Embedding model is not ready",
                "modelName": MODEL_NAME,
                "modelSource": MODEL_PATH if MODEL_PATH else MODEL_NAME,
                "error": str(ex),
            },
        ) from ex
    vectors = model.encode(
        texts,
        normalize_embeddings=NORMALIZE,
        convert_to_numpy=True,
    )
    usage_tokens = estimate_tokens(texts)
    data = [
        EmbeddingItem(
            index=index,
            embedding=vector.astype(float).tolist(),
        )
        for index, vector in enumerate(vectors)
    ]
    return EmbeddingResponse(
        data=data,
        model=request.model or MODEL_NAME,
        usage=EmbeddingUsage(
            prompt_tokens=usage_tokens,
            total_tokens=usage_tokens,
        ),
    )
