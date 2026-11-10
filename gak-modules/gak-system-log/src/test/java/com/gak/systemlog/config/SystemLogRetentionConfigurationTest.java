package com.gak.systemlog.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.gak.framework.exception.SystemExceptionCapture;
import com.gak.systemlog.mapper.SystemExceptionLogMapper;
import com.gak.systemlog.service.SystemExceptionLogPurger;
import com.gak.systemlog.service.SystemExceptionLogRetentionJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 仅开启清理时校验配置；关闭时不会注册任何删数 Bean。 */
class SystemLogRetentionConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SystemLogClockConfiguration.class, SystemLogRetentionConfiguration.class)
            .withBean(SystemExceptionLogMapper.class, () -> mock(SystemExceptionLogMapper.class))
            .withBean(SystemExceptionCapture.class, () -> mock(SystemExceptionCapture.class));

    @Test
    void disabledByDefaultAndExplicitFalse() {
        runner.withPropertyValues("gak.system-log.cleanup.enabled=false",
                    "gak.system-log.retention-days=0", "gak.system-log.cleanup.cron=invalid").run(context -> {
                        assertThat(context).hasNotFailed().doesNotHaveBean(SystemExceptionLogRetentionJob.class)
                                .doesNotHaveBean(SystemExceptionLogPurger.class);
                    });
        runner.run(context -> assertThat(context).doesNotHaveBean(SystemExceptionLogRetentionJob.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"42501", "42883", "55P03", "57014"})
    void enabledRegistersBeansAndSqlFailureDoesNotCapture(String sqlState) {
        runner.withPropertyValues("gak.system-log.cleanup.enabled=true").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(SystemExceptionLogRetentionJob.class);
            SystemExceptionLogMapper mapper = context.getBean(SystemExceptionLogMapper.class);
            when(mapper.tryCleanupLock(anyInt(), anyInt())).thenReturn(true);
            when(mapper.purgeExpired(any(), anyInt())).thenThrow(new org.springframework.jdbc.UncategorizedSQLException(
                    "synthetic", "synthetic", new java.sql.SQLException("synthetic", sqlState)));
            context.getBean(SystemExceptionLogRetentionJob.class).cleanup();
            verifyNoInteractions(context.getBean(SystemExceptionCapture.class));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"gak.system-log.retention-days=29", "gak.system-log.retention-days=0",
            "gak.system-log.retention-days=-1",
            "gak.system-log.cleanup.batch-size=0", "gak.system-log.cleanup.batch-size=1001",
            "gak.system-log.cleanup.max-batches-per-run=0", "gak.system-log.cleanup.batch-interval-ms=-1",
            "gak.system-log.cleanup.cron=invalid", "gak.system-log.cleanup.statement-timeout=0s",
            "gak.system-log.cleanup.statement-timeout=-1s"})
    void invalidEnabledConfigurationFailsStartup(String invalid) {
        runner.withPropertyValues("gak.system-log.cleanup.enabled=true", invalid)
                .run(context -> assertThat(context).hasFailed());
    }
}
