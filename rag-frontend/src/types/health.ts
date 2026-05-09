export type HealthStatus = {
  status: string;
  serviceName: string;
  activeProfiles: string[];
  components: Record<string, string>;
  checkedAt: string;
};

export type RedisProbe = {
  key: string;
  writtenValue: string;
  cachedValue: string;
  matched: boolean;
};
