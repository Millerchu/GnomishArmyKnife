package com.gak.systemlog.config;

import com.gak.systemlog.mapper.SystemExceptionLogMapper;
import com.gak.systemlog.service.SystemExceptionLogPurger;
import com.gak.systemlog.service.SystemExceptionLogRetentionJob;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 关闭时不注册任务或删数器，不依赖附件模块的调度开关。 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(SystemLogRetentionProperties.class)
@ConditionalOnProperty(prefix = "gak.system-log.cleanup", name = "enabled", havingValue = "true")
public class SystemLogRetentionConfiguration {

    @Bean
    public SystemExceptionLogPurger systemExceptionLogPurger(SystemExceptionLogMapper mapper,
            SystemLogRetentionProperties properties,
            @Value("${gak.system-log.retention-days:30}") int retentionDays) {
        properties.validate(retentionDays);
        return new SystemExceptionLogPurger(mapper, properties);
    }

    @Bean
    public SystemExceptionLogRetentionJob systemExceptionLogRetentionJob(SystemExceptionLogPurger purger,
            SystemLogRetentionProperties properties, @Qualifier("systemLogClock") Clock clock,
            @Value("${gak.system-log.retention-days:30}") int retentionDays) {
        return new SystemExceptionLogRetentionJob(purger, properties, clock, retentionDays);
    }
}
