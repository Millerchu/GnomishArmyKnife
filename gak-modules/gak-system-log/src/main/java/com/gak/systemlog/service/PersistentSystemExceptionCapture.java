package com.gak.systemlog.service;

import com.gak.framework.exception.SystemExceptionCapture;
import com.gak.systemlog.domain.SystemExceptionLog;
import com.gak.systemlog.enums.SystemExceptionSource;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 把未处理异常脱敏后异步写入。写入线程与业务事务分离，失败只记类型，不向外抛。
 */
@Service
public class PersistentSystemExceptionCapture implements SystemExceptionCapture {

    static final String WRITER_THREAD = "system-exception-log";
    private static final int QUEUE_CAPACITY = 32;
    private static final long WRITE_WAIT_SECONDS = 2L;
    private static final Logger LOG = LoggerFactory.getLogger(PersistentSystemExceptionCapture.class);

    private final SystemExceptionSanitizer sanitizer;
    private final SystemExceptionLogWriter writer;
    private final ExecutorService writerExecutor;
    private final ThreadLocal<Boolean> recording = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 类里还有供测试使用的构造器。多个构造器时 Spring 不会自动选择，
     * 未标注时会去找无参构造并在启动期失败。
     */
    @Autowired
    public PersistentSystemExceptionCapture(SystemExceptionLogWriter writer) {
        this(new SystemExceptionSanitizer(), writer, newWriterExecutor());
    }

    PersistentSystemExceptionCapture(SystemExceptionSanitizer sanitizer,
                                     SystemExceptionLogWriter writer,
                                     ExecutorService writerExecutor) {
        this.sanitizer = sanitizer;
        this.writer = writer;
        this.writerExecutor = writerExecutor;
    }

    @Override
    public void capture(Throwable exception, String source) {
        if (exception == null || recording.get()) {
            return;
        }
        if (!SystemExceptionSource.contains(source)) {
            LOG.warn("忽略未知系统异常来源，未入库");
            return;
        }
        recording.set(Boolean.TRUE);
        try {
            String httpMethod = null;
            String requestPath = null;
            if (SystemExceptionSource.HTTP.name().equals(source)) {
                HttpServletRequest request = currentRequest();
                if (request != null) {
                    httpMethod = request.getMethod();
                    requestPath = MatchedRouteTemplate.read(request);
                }
            }
            SanitizedSystemException sanitized = sanitizer.sanitize(exception, httpMethod, requestPath);
            persist(toLog(sanitized, source));
        } catch (Throwable failure) {
            LOG.warn("系统异常日志准备失败，原异常继续按原路径返回。失败类型={}", failure.getClass().getName());
        } finally {
            recording.remove();
        }
    }

    /**
     * 只有 HTTP 来源才取请求。路径只读 Spring 匹配模板，不读 requestURI。
     */
    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest();
    }

    private SystemExceptionLog toLog(SanitizedSystemException sanitized, String source) {
        // 无偏移的本地时间，取 JVM 默认时区。应用没有把时区固定为 Asia/Shanghai。
        LocalDateTime now = LocalDateTime.now();
        SystemExceptionLog log = new SystemExceptionLog();
        log.setOccurredAt(now);
        log.setCreatedAt(now);
        log.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        log.setSource(source);
        log.setExceptionType(sanitized.exceptionType());
        log.setSummary(sanitized.summary());
        log.setStackTrace(sanitized.stackTrace());
        log.setStackTruncated(sanitized.stackTruncated());
        log.setHttpMethod(sanitized.httpMethod());
        log.setRequestPath(sanitized.requestPath());
        return log;
    }

    private void persist(SystemExceptionLog log) {
        if (WRITER_THREAD.equals(Thread.currentThread().getName())) {
            writer.insert(log);
            return;
        }
        Future<?> pending;
        try {
            pending = writerExecutor.submit(() -> writer.insert(log));
        } catch (RejectedExecutionException rejected) {
            LOG.warn("系统异常日志队列已满，本次未入库。失败类型={}", rejected.getClass().getName());
            return;
        }
        try {
            pending.get(WRITE_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            // get 只结束调用方等待，不 cancel 任务，也不保证数据库操作在此时结束。
            LOG.warn("系统异常日志写入在 {} 秒内未返回，已停止等待且未取消任务。原请求继续返回，数据库写入可能仍在进行",
                    WRITE_WAIT_SECONDS);
        } catch (ExecutionException execution) {
            Throwable cause = execution.getCause() == null ? execution : execution.getCause();
            LOG.warn("系统异常日志写入失败，原异常继续按原路径返回。失败类型={}", cause.getClass().getName());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            LOG.warn("等待系统异常日志写入时被中断，原异常继续按原路径返回");
        }
    }

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        writerExecutor.shutdownNow();
    }

    private static ExecutorService newWriterExecutor() {
        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, WRITER_THREAD);
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }
}
