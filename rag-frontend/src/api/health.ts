import { apiClient } from "./client";
import type { HealthStatus, RedisProbe } from "../types/health";

/** 查询系统健康状态。 */
export function getHealthStatus() {
  return apiClient.get<HealthStatus>("/api/health");
}

/** 查询 Redis 探针结果。 */
export function getRedisProbe() {
  return apiClient.get<RedisProbe>("/api/health/redis-probe");
}
