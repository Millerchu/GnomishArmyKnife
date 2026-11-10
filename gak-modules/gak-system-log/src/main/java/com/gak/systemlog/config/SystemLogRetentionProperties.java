package com.gak.systemlog.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

/** 清理配置仅在显式开启时绑定、校验。 */
@ConfigurationProperties("gak.system-log.cleanup")
public class SystemLogRetentionProperties {

    private static final int MIN_RETENTION_DAYS = 30;
    private static final int MAX_BATCH_SIZE = 1000;

    private boolean enabled = false;
    private String cron = "0 10 4 * * *";
    private int batchSize = 500;
    private int maxBatchesPerRun = 40;
    private long batchIntervalMs = 100;
    private Duration statementTimeout = Duration.ofSeconds(10);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxBatchesPerRun() {
        return maxBatchesPerRun;
    }

    public void setMaxBatchesPerRun(int maxBatchesPerRun) {
        this.maxBatchesPerRun = maxBatchesPerRun;
    }

    public long getBatchIntervalMs() {
        return batchIntervalMs;
    }

    public void setBatchIntervalMs(long batchIntervalMs) {
        this.batchIntervalMs = batchIntervalMs;
    }

    public Duration getStatementTimeout() {
        return statementTimeout;
    }

    public void setStatementTimeout(Duration statementTimeout) {
        this.statementTimeout = statementTimeout;
    }

    /** 关闭时允许保留旧 retention-days 值，开启后必须与函数硬下限一致。 */
    public void validate(int retentionDays) {
        if (retentionDays < MIN_RETENTION_DAYS || batchSize < 1 || batchSize > MAX_BATCH_SIZE
                || maxBatchesPerRun < 1 || batchIntervalMs < 0) {
            throw new IllegalStateException("系统日志清理配置非法：retention-days >= 30、batch-size 1–1000、"
                    + "max-batches-per-run >= 1、batch-interval-ms >= 0");
        }
        if (cron == null || !CronExpression.isValidExpression(cron)) {
            throw new IllegalStateException("gak.system-log.cleanup.cron 不合法");
        }
        if (statementTimeout == null || statementTimeout.toMillis() < 1) {
            throw new IllegalStateException("系统日志清理 statement-timeout 必须至少为 1ms");
        }
    }
}
