package com.gak.systemlog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.systemlog.domain.SystemExceptionLog;
import com.gak.systemlog.mapper.SystemExceptionLogMapper;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 声明 REQUIRES_NEW，避免和业务事务绑在同一次提交里。timeout = 2 只是事务注解上的秒数。
 * 这两项只有经过 Spring 代理调用才生效；测试里直接 new 本类不会启动事务，也不能证明锁等待行为。
 * 本类只负责写入保护；按时间清理由独立任务执行。
 */
@Service
public class SystemExceptionLogWriter {

    private static final Logger LOG = LoggerFactory.getLogger(SystemExceptionLogWriter.class);

    private final SystemExceptionLogMapper mapper;
    private final int maxRows;
    private final int retentionDays;
    private final AtomicBoolean capacityWarned = new AtomicBoolean(false);

    public SystemExceptionLogWriter(SystemExceptionLogMapper mapper,
                                    @Value("${gak.system-log.max-rows:20000}") int maxRows,
                                    @Value("${gak.system-log.retention-days:30}") int retentionDays) {
        if (maxRows < 1) {
            throw new IllegalStateException("gak.system-log.max-rows 必须大于 0");
        }
        if (retentionDays < 0) {
            throw new IllegalStateException("gak.system-log.retention-days 不能为负数");
        }
        this.mapper = mapper;
        this.maxRows = maxRows;
        this.retentionDays = retentionDays;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 2)
    public void insert(SystemExceptionLog log) {
        Long existing = mapper.selectCount(new QueryWrapper<>());
        long stored = existing == null ? 0L : existing;
        if (stored >= maxRows) {
            if (capacityWarned.compareAndSet(false, true)) {
                LOG.warn("系统异常日志已达 {} 条上限，新的异常不再入库。retention-days={}，恢复容量后可重新写入；清理是否启用取决于 cleanup.enabled",
                        maxRows, retentionDays);
            }
            return;
        }
        if (mapper.insert(log) > 0) {
            capacityWarned.set(false);
        }
    }
}
