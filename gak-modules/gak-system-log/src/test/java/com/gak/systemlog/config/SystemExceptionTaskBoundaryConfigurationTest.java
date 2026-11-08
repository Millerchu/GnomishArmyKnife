package com.gak.systemlog.config;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.gak.framework.exception.SystemExceptionCapture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.ErrorHandler;

/** 自动配置线程池和调度器只采集未捕获异常，采集失败仍抛出原异常。 */
class SystemExceptionTaskBoundaryConfigurationTest {

    @Test
    void executorRethrowsOriginalWhenCaptureFails() {
        SystemExceptionCapture capture = mock(SystemExceptionCapture.class);
        doThrow(new IllegalStateException("log down")).when(capture).capture(any(), any());
        AtomicReference<TaskDecorator> decorator = new AtomicReference<>();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor() {
            @Override
            public void setTaskDecorator(TaskDecorator taskDecorator) {
                decorator.set(taskDecorator);
            }
        };
        new SystemExceptionTaskBoundaryConfiguration()
                .systemExceptionTaskExecutorCustomizer(capture)
                .customize(executor);
        IllegalStateException original = new IllegalStateException("original");
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> decorator.get().decorate(() -> {
                    throw original;
                }).run());
        assertSame(original, thrown);
        verify(capture).capture(original, SystemExceptionCapture.SOURCE_ASYNC_TASK);
    }

    @Test
    void schedulerUsesSchedulerSource() {
        SystemExceptionCapture capture = mock(SystemExceptionCapture.class);
        AtomicReference<ErrorHandler> handler = new AtomicReference<>();
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler() {
            @Override
            public void setErrorHandler(ErrorHandler errorHandler) {
                handler.set(errorHandler);
            }
        };
        new SystemExceptionTaskBoundaryConfiguration()
                .systemExceptionTaskSchedulerCustomizer(capture)
                .customize(scheduler);
        IllegalStateException failure = new IllegalStateException("password=secret");
        handler.get().handleError(failure);
        verify(capture).capture(eq(failure), eq(SystemExceptionCapture.SOURCE_SCHEDULER));
    }
}
