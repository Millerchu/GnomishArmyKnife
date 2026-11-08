package com.gak.systemlog.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.gak.framework.exception.SystemExceptionCapture;
import com.gak.systemlog.mapper.SystemExceptionLogMapper;
import com.gak.systemlog.service.PersistentSystemExceptionCapture;
import com.gak.systemlog.service.SystemExceptionLogWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.boot.task.ThreadPoolTaskSchedulerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * 用真实 Spring 容器装配捕获器和线程池定制器。不连接数据库，也不启动完整应用。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SystemLogSpringContextTest.Config.class)
@TestPropertySource(properties = {
        "gak.system-log.max-rows=20",
        "gak.system-log.retention-days=30"
})
class SystemLogSpringContextTest {

    @Autowired
    private SystemExceptionCapture capture;

    @Autowired
    private ThreadPoolTaskExecutorCustomizer systemExceptionTaskExecutorCustomizer;

    @Autowired
    private ThreadPoolTaskSchedulerCustomizer systemExceptionTaskSchedulerCustomizer;

    @Test
    void wiresCaptureAndTaskCustomizers() {
        assertNotNull(capture);
        assertNotNull(systemExceptionTaskExecutorCustomizer);
        assertNotNull(systemExceptionTaskSchedulerCustomizer);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        assertDoesNotThrow(() -> systemExceptionTaskExecutorCustomizer.customize(executor));
        assertDoesNotThrow(() -> systemExceptionTaskSchedulerCustomizer.customize(scheduler));
    }

    @Configuration
    @Import({
            SystemExceptionTaskBoundaryConfiguration.class,
            PersistentSystemExceptionCapture.class,
            SystemExceptionLogWriter.class
    })
    static class Config {

        @Bean
        static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        SystemExceptionLogMapper systemExceptionLogMapper() {
            return mock(SystemExceptionLogMapper.class);
        }
    }
}
