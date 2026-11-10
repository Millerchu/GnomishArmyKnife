package com.gak.systemlog.service;

import com.gak.systemlog.config.SystemLogRetentionProperties;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** 失败只记应用日志，绝不交给调度器的异常采集边界，避免递归写入。 */
public class SystemExceptionLogRetentionJob {

    private static final Logger LOG = LoggerFactory.getLogger(SystemExceptionLogRetentionJob.class);
    private final SystemExceptionLogPurger purger;
    private final SystemLogRetentionProperties properties;
    private final Clock clock;
    private final int retentionDays;

    public SystemExceptionLogRetentionJob(SystemExceptionLogPurger purger,
            SystemLogRetentionProperties properties, Clock clock, int retentionDays) {
        this.purger = purger;
        this.properties = properties;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${gak.system-log.cleanup.cron:0 10 4 * * *}", zone = "Asia/Shanghai")
    public void cleanup() {
        long started = System.nanoTime();
        LocalDateTime cutoff = null;
        int batches = 0;
        long deleted = 0;
        try {
            cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
            LOG.info("系统日志清理开始 cutoff={} batchSize={} maxBatchesPerRun={}",
                    cutoff, properties.getBatchSize(), properties.getMaxBatchesPerRun());
            while (batches < properties.getMaxBatchesPerRun()) {
                OptionalInt result = purger.purgeOneBatch(cutoff, properties.getBatchSize());
                if (result.isEmpty()) {
                    LOG.info("系统日志清理跳过：未获 advisory 锁 cutoff={} batches={} deleted={}", cutoff, batches, deleted);
                    break;
                }
                batches++;
                deleted += result.getAsInt();
                if (result.getAsInt() < properties.getBatchSize() || batches >= properties.getMaxBatchesPerRun()) {
                    break;
                }
                if (properties.getBatchIntervalMs() > 0) {
                    Thread.sleep(properties.getBatchIntervalMs());
                }
            }
        } catch (Throwable failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // 不打印异常消息或数据，仅保留 SQLSTATE 和类型以定位权限、函数缺失、超时等失败。
            LOG.warn("系统日志清理失败/跳过 cutoff={} batches={} deleted={} sqlState={} failureType={}",
                    cutoff, batches, deleted, sqlState(failure), failure.getClass().getName());
        } finally {
            LOG.info("系统日志清理结束 cutoff={} batchSize={} batches={} deleted={} elapsedMs={}",
                    cutoff, properties.getBatchSize(), batches, deleted, (System.nanoTime() - started) / 1_000_000);
        }
    }

    private String sqlState(Throwable failure) {
        java.util.Set<Throwable> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
        }
        return null;
    }
}
