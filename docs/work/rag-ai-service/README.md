# rag-ai-service Work

## Position

`rag-ai-service` 是当前项目的 Python AI Gateway，负责：

1. embedding 和 chat completion 的统一对外接口。
2. provider 适配、超时、重试和错误映射。
3. requestId 透传、usage 和最小观测口径。
4. 为后续 rerank、vLLM、本地模型和 evaluation 预留扩展面。

## Read Here First

1. [current-status.md](./current-status.md)
2. [plan.md](./plan.md)

## Notes

1. `plan.md` 是该板块统一的计划入口，当前内容按“当前快照 + 下一阶段计划”持续维护。
2. 后续如果进入新阶段，优先在 `plan.md` 内追加和收敛，不再用 `phaseN-plan.md` 这种单独命名。

## Related Boards

1. [rag-backend](../rag-backend/README.md)
2. [rag-frontend](../rag-frontend/README.md)
3. [RFC Index](../../rfcs/README.md)
