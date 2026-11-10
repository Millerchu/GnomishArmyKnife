package com.gak.systemlog.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 写入和清理共享上海时钟，测试可按名称替换固定时钟。 */
@Configuration(proxyBeanMethods = false)
public class SystemLogClockConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "systemLogClock")
    public Clock systemLogClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}
