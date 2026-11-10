package com.gak.systemlog.service;

import com.gak.systemlog.config.SystemLogRetentionProperties;
import com.gak.systemlog.mapper.SystemExceptionLogMapper;
import java.time.LocalDateTime;
import java.util.OptionalInt;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 每批经独立 Spring 事务代理调用，事务结束自动释放锁及本地超时。 */
public class SystemExceptionLogPurger {

    // 双 int4 锁与 WorkLog 的 userId、Message 的 hashtextextended 单 bigint 键空间互不相交。
    // 仓库既有用法没有双 int4 锁；本组合专用于系统日志清理。
    private static final int LOCK_NAMESPACE = 0x47414B;
    private static final int LOCK_RESOURCE = 0x534C;

    private final SystemExceptionLogMapper mapper;
    private final SystemLogRetentionProperties properties;

    public SystemExceptionLogPurger(SystemExceptionLogMapper mapper, SystemLogRetentionProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    /** empty 表示未获锁，0 表示拿到锁但无过期行；调用方据此中止本轮。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 15)
    public OptionalInt purgeOneBatch(LocalDateTime cutoff, int batchSize) {
        mapper.setCleanupStatementTimeout(properties.getStatementTimeout().toMillis() + "ms");
        if (!mapper.tryCleanupLock(LOCK_NAMESPACE, LOCK_RESOURCE)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(mapper.purgeExpired(cutoff, batchSize));
    }
}
