from __future__ import annotations

import json
from typing import Any


def summarize_observation(output: dict[str, Any]) -> dict[str, Any]:
    """裁剪工具输出，再展示给模型或前端时间线。"""
    summary: dict[str, Any] = {}
    # 只保留诊断决策最常用的稳定字段，避免把大块正文或无关细节塞回 planner。
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
            # 检索分支只保留数量、模式和耗时摘要，完整来源由工具原始输出保留。
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
            # 列表型证据只保留前三条，控制 prompt 和时间线体积。
            summary[key] = value[:3]
    return summary or {"keys": sorted(output.keys())[:10]}


def to_json(value: dict[str, Any]) -> str:
    """序列化紧凑且稳定的 JSON，供 Java 持久化和测试断言使用。"""
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
