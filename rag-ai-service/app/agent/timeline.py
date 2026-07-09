from __future__ import annotations

import json
from typing import Any


def summarize_observation(output: dict[str, Any]) -> dict[str, Any]:
    """Trim tool output before it is shown to the model or frontend timeline."""
    summary: dict[str, Any] = {}
    for key in [
        "status",
        "kbCode",
        "questionAnsweringReady",
        "reembedRequired",
        "reembedInProgress",
        "nextStep",
        "statusCounts",
        "signals",
        "question",
        "topK",
        "defaultMode",
        "defaultTopK",
        "maxTopK",
        "denseCandidateLimit",
        "keywordCandidateLimit",
        "fusionK",
        "keywordStrategy",
        "keywordMinTokenLength",
        "keywordMinHitThreshold",
        "summary",
        "command",
        "mode",
    ]:
        if key in output:
            summary[key] = output[key]
    for key in ["dense", "hybrid"]:
        value = output.get(key)
        if isinstance(value, dict):
            summary[key] = {
                branch_key: value[branch_key]
                for branch_key in [
                    "retrievalMode",
                    "hitCount",
                    "denseHitCount",
                    "keywordHitCount",
                    "fusionStrategy",
                    "totalDurationMs",
                ]
                if branch_key in value
            }
    for key in ["failedTasks", "failedDocuments", "sources"]:
        value = output.get(key)
        if isinstance(value, list):
            summary[key] = value[:3]
    return summary or {"keys": sorted(output.keys())[:10]}


def to_json(value: dict[str, Any]) -> str:
    """Serialize compact stable JSON for Java persistence and tests."""
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
