package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 索引任务相关配置。
 */
@ConfigurationProperties(prefix = "rag.indexing")
public class RagIndexingProperties {

    private int maxRetryCount = 3;

    private Recovery recovery = new Recovery();

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public Recovery getRecovery() {
        return recovery;
    }

    public void setRecovery(Recovery recovery) {
        this.recovery = recovery;
    }

    public static class Recovery {

        private boolean enabled = true;

        private long staleAfterSeconds = 600;

        private int scanLimit = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getStaleAfterSeconds() {
            return staleAfterSeconds;
        }

        public void setStaleAfterSeconds(long staleAfterSeconds) {
            this.staleAfterSeconds = staleAfterSeconds;
        }

        public int getScanLimit() {
            return scanLimit;
        }

        public void setScanLimit(int scanLimit) {
            this.scanLimit = scanLimit;
        }
    }
}
