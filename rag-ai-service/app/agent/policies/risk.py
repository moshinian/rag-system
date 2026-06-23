from __future__ import annotations

from app.agent.state import AgentToolDefinition


def requires_confirmation(tool: AgentToolDefinition) -> bool:
    """判断工具是否必须走 human-in-the-loop 确认。"""
    return (
        # 只要工具显式要求确认、不是只读模式，或风险为 MEDIUM/HIGH，都不能直接执行。
        tool.requires_confirmation
        or tool.execution_mode != "READ_ONLY"
        or tool.risk_level in {"MEDIUM", "HIGH"}
    )
