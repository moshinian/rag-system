import { apiClient } from "./client";
import type { HealthStatus, RedisProbe } from "../types/health";

export function getHealthStatus() {
  return apiClient.get<HealthStatus>("/api/health");
}

export function getRedisProbe() {
  return apiClient.get<RedisProbe>("/api/health/redis-probe");
}
