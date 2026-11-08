package com.gak.systemlog.config;

import com.gak.framework.exception.SystemExceptionCapture;
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.boot.task.ThreadPoolTaskSchedulerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 只覆盖 Spring Boot 自动配置的 applicationTaskExecutor 与 taskScheduler 上未被捕获的异常。
 * 任务内部已经捕获的异常、消息模块虚拟线程，以及其他自建线程池，不经过这里。
 */
@Configuration
public class SystemExceptionTaskBoundaryConfiguration {

    @Bean
    public ThreadPoolTaskExecutorCustomizer systemExceptionTaskExecutorCustomizer(SystemExceptionCapture capture) {
        return executor -> executor.setTaskDecorator(runnable -> () -> runAndCapture(capture, runnable));
    }

    @Bean
    public ThreadPoolTaskSchedulerCustomizer systemExceptionTaskSchedulerCustomizer(SystemExceptionCapture capture) {
        return scheduler -> scheduler.setErrorHandler(throwable ->
                captureQuietly(capture, throwable, SystemExceptionCapture.SOURCE_SCHEDULER));
    }

    static void runAndCapture(SystemExceptionCapture capture, Runnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException | Error throwable) {
            captureQuietly(capture, throwable, SystemExceptionCapture.SOURCE_ASYNC_TASK);
            throw throwable;
        }
    }

    private static void captureQuietly(SystemExceptionCapture capture, Throwable throwable, String source) {
        try {
            capture.capture(throwable, source);
        } catch (Throwable ignored) {
            // 采集失败不能替换任务原本抛出的异常。
        }
    }
}
