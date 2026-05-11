export type HealthComponentStatus = {
  status: string;
  category: string;
  endpoint: string | null;
  provider: string | null;
  model: string | null;
  latencyMs: number | null;
  detail: string | null;
  errorMessage: string | null;
  checkedAt: string;
};

export type HealthStatus = {
  status: string;
  serviceName: string;
  activeProfiles: string[];
  components: Record<string, HealthComponentStatus>;
  checkedAt: string;
};

export type RedisProbe = {
  key: string;
  writtenValue: string;
  cachedValue: string;
  matched: boolean;
};
