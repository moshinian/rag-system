import { Alert, Badge, Collapse, Empty, Space, Tag, Timeline, Typography } from "antd";
import type { AgentRunEvent, AgentRunEventConnectionStatus, AgentRunEventType } from "../../types/agent";
import { formatDateTime, truncateText } from "../../utils/format";
import { JsonBlock } from "./json-block";

type AgentRunEventTimelineProps = {
  events: AgentRunEvent[];
  connectionStatus: AgentRunEventConnectionStatus;
  connectionError?: string;
};

/** 展示 Java SSE 推送的实时 Agent Runtime Timeline。 */
export function AgentRunEventTimeline({ events, connectionStatus, connectionError }: AgentRunEventTimelineProps) {
  return (
    <Space direction="vertical" size="middle" style={{ width: "100%" }}>
      <Space wrap>
        {renderConnectionStatus(connectionStatus)}
        <Typography.Text type="secondary">事件数：{events.length}</Typography.Text>
      </Space>
      {connectionError && connectionStatus === "RECONNECTING" ? <Alert type="warning" showIcon message={connectionError} /> : null}
      {events.length === 0 ? (
        <Empty description="暂无实时事件；创建 run 后会自动订阅 Java SSE" />
      ) : (
        <Timeline
          items={events.map((event) => ({
            key: event.eventId,
            color: eventColor(event.type),
            children: <AgentRunEventItem event={event} />
          }))}
        />
      )}
    </Space>
  );
}

function AgentRunEventItem({ event }: { event: AgentRunEvent }) {
  return (
    <Space direction="vertical" size="small" style={{ width: "100%" }}>
      <Space wrap>
        <Typography.Text strong>{eventTitle(event)}</Typography.Text>
        <Tag color={eventTagColor(event.type)}>{event.type}</Tag>
        {event.status ? <Tag>{event.status}</Tag> : null}
        {event.nodeName ? <Tag color="geekblue">{event.nodeName}</Tag> : null}
        {event.toolName ? <Tag color="blue">{event.toolName}</Tag> : null}
        {event.terminal ? <Tag color="purple">terminal</Tag> : null}
      </Space>
      <Space wrap size="small">
        <Typography.Text type="secondary">#{event.databaseId}</Typography.Text>
        <Typography.Text type="secondary">{event.eventId}</Typography.Text>
        <Typography.Text type="secondary">{formatDateTime(event.createdAt)}</Typography.Text>
      </Space>
      {event.message ? <Typography.Text>{event.message}</Typography.Text> : null}
      {event.payloadJson ? (
        <Collapse
          size="small"
          ghost
          items={[
            {
              key: "payload",
              label: "事件 payload",
              children: <JsonBlock value={event.payloadJson} maxHeight={260} />
            }
          ]}
        />
      ) : null}
    </Space>
  );
}

function renderConnectionStatus(status: AgentRunEventConnectionStatus) {
  if (status === "OPEN") {
    return <Badge status="success" text="已连接" />;
  }
  if (status === "RECONNECTING") {
    return <Badge status="warning" text="连接中断，正在重连" />;
  }
  if (status === "ENDED") {
    return <Badge status="default" text="已结束" />;
  }
  if (status === "CONNECTING") {
    return <Badge status="processing" text="连接中" />;
  }
  return <Badge status="default" text="未连接" />;
}

function eventTitle(event: AgentRunEvent) {
  if (event.nodeName && event.toolName) {
    return `${event.nodeName} / ${event.toolName}`;
  }
  if (event.nodeName) {
    return event.nodeName;
  }
  if (event.toolName) {
    return event.toolName;
  }
  return event.message ? truncateText(event.message, 48) : event.type;
}

function eventColor(type: AgentRunEventType) {
  if (type === "RUN_COMPLETED") return "green";
  if (type === "RUN_FAILED" || type === "STEP_FAILED" || type === "TOOL_CALL_FAILED") return "red";
  if (type === "RUN_WAITING_CONFIRMATION" || type === "ACTION_RECOMMENDED") return "orange";
  if (type.endsWith("_STARTED")) return "blue";
  return "gray";
}

function eventTagColor(type: AgentRunEventType) {
  if (type === "RUN_COMPLETED") return "green";
  if (type === "RUN_FAILED" || type === "STEP_FAILED" || type === "TOOL_CALL_FAILED") return "red";
  if (type === "RUN_WAITING_CONFIRMATION" || type === "ACTION_RECOMMENDED") return "gold";
  if (type === "PLANNER_DECISION") return "cyan";
  if (type.startsWith("TOOL_CALL")) return "blue";
  return "default";
}
