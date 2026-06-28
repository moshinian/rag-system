import { useEffect, useMemo, useRef, useState } from "react";
import type { Dispatch, SetStateAction } from "react";
import type { AgentRunEvent, AgentRunEventConnectionStatus, AgentRunEventType } from "../types/agent";

const agentRunEventTypes: AgentRunEventType[] = [
  "RUN_STARTED",
  "STEP_STARTED",
  "STEP_COMPLETED",
  "STEP_FAILED",
  "PLANNER_DECISION",
  "TOOL_CALL_STARTED",
  "TOOL_CALL_COMPLETED",
  "TOOL_CALL_FAILED",
  "OBSERVATION_CREATED",
  "ACTION_RECOMMENDED",
  "RUN_COMPLETED",
  "RUN_FAILED",
  "RUN_WAITING_CONFIRMATION"
];

const terminalEventTypes = new Set<AgentRunEventType>(["RUN_COMPLETED", "RUN_FAILED", "RUN_WAITING_CONFIRMATION"]);

type UseAgentRunEventsOptions = {
  kbCode?: string;
  runCode?: string;
  enabled?: boolean;
  onTerminal?: (event: AgentRunEvent) => void;
};

type UseAgentRunEventsResult = {
  events: AgentRunEvent[];
  connectionStatus: AgentRunEventConnectionStatus;
  error?: string;
  reset: () => void;
};

/** 订阅 Java 暴露给 React 的 Agent SSE；这里不会直连 Python。 */
export function useAgentRunEvents({
  kbCode,
  runCode,
  enabled = true,
  onTerminal
}: UseAgentRunEventsOptions): UseAgentRunEventsResult {
  const [events, setEvents] = useState<AgentRunEvent[]>([]);
  const [connectionStatus, setConnectionStatus] = useState<AgentRunEventConnectionStatus>("IDLE");
  const [error, setError] = useState<string>();
  const eventIdsRef = useRef<Set<string>>(new Set());
  const sourceRef = useRef<EventSource | null>(null);
  const terminalRef = useRef(false);
  const onTerminalRef = useRef(onTerminal);

  useEffect(() => {
    onTerminalRef.current = onTerminal;
  }, [onTerminal]);

  useEffect(() => {
    eventIdsRef.current = new Set();
    setEvents([]);
    setError(undefined);
    terminalRef.current = false;

    if (!enabled || !kbCode || !runCode) {
      closeSource(sourceRef.current);
      sourceRef.current = null;
      setConnectionStatus("IDLE");
      return;
    }

    setConnectionStatus("CONNECTING");
    const source = new EventSource(`/api/knowledge-bases/${encodeURIComponent(kbCode)}/agent/runs/${encodeURIComponent(runCode)}/events`);
    sourceRef.current = source;

    source.onopen = () => {
      setError(undefined);
      setConnectionStatus("OPEN");
    };

    source.onerror = () => {
      if (terminalRef.current) {
        return;
      }
      // 浏览器 EventSource 会自动携带 Last-Event-ID 重连，前端只展示连接状态，不做轮询。
      setConnectionStatus("RECONNECTING");
      setError("连接中断，正在重连");
    };

    const removeHandlers = agentRunEventTypes.map((type) => {
      const handler = (message: MessageEvent<string>) => {
        const event = parseAgentRunEvent(message);
        if (!event) {
          return;
        }
        appendEvent(eventIdsRef.current, setEvents, event);
        if (event.terminal || terminalEventTypes.has(event.type)) {
          terminalRef.current = true;
          setConnectionStatus("ENDED");
          closeSource(source);
          onTerminalRef.current?.(event);
        }
      };
      source.addEventListener(type, handler);
      return () => source.removeEventListener(type, handler);
    });

    return () => {
      removeHandlers.forEach((remove) => remove());
      closeSource(source);
      if (sourceRef.current === source) {
        sourceRef.current = null;
      }
    };
  }, [enabled, kbCode, runCode]);

  const reset = useMemo(
    () => () => {
      eventIdsRef.current = new Set();
      setEvents([]);
      setError(undefined);
      terminalRef.current = false;
      setConnectionStatus(enabled && kbCode && runCode ? "CONNECTING" : "IDLE");
    },
    [enabled, kbCode, runCode]
  );

  return { events, connectionStatus, error, reset };
}

function parseAgentRunEvent(message: MessageEvent<string>) {
  try {
    const event = JSON.parse(message.data) as AgentRunEvent;
    if (!event.eventId || !event.type) {
      return undefined;
    }
    return event;
  } catch {
    return undefined;
  }
}

function appendEvent(
  eventIds: Set<string>,
  setEvents: Dispatch<SetStateAction<AgentRunEvent[]>>,
  event: AgentRunEvent
) {
  if (eventIds.has(event.eventId)) {
    return;
  }
  eventIds.add(event.eventId);
  setEvents((current) => [...current, event].sort((left, right) => left.databaseId - right.databaseId));
}

function closeSource(source: EventSource | null) {
  if (source && source.readyState !== EventSource.CLOSED) {
    source.close();
  }
}
