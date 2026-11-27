package com.gak.message.service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 单实例在线连接；通知允许遗漏，持久化收件箱负责恢复。 */
@Service
public class MessageStreamService {
    private static final Logger LOG = LoggerFactory.getLogger(MessageStreamService.class);
    private static final long HEARTBEAT_SECONDS = 20;
    private static final long METRIC_INTERVAL_SECONDS = 60;
    private static final long SEND_TIMEOUT_SECONDS = 5;
    private static final long CONNECTION_TIMEOUT_MS = 30 * 60 * 1000L;
    private static final int MAX_CONNECTIONS = 1000;
    private static final int MAX_USER_CONNECTIONS = 12;
    private final Map<Long, Set<Connection>> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("gak-message-sse-clock").factory());
    private final ExecutorService writers = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("gak-message-sse-writer-", 0).factory());
    private final Semaphore capacity = new Semaphore(MAX_CONNECTIONS);
    private final AtomicLong failures = new AtomicLong();
    private boolean stopping;

    public MessageStreamService() {
        scheduler.scheduleAtFixedRate(this::heartbeat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(() -> LOG.info("消息连接数={}, 推送失败数={}",
                MAX_CONNECTIONS - capacity.availablePermits(), failures.get()),
                METRIC_INTERVAL_SECONDS, METRIC_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void heartbeat() {
        try {
            connections.values().forEach(group -> group.forEach(connection -> send(connection, "heartbeat", Map.of())));
        } catch (RuntimeException exception) {
            LOG.warn("SSE 心跳推送失败", exception);
        }
    }

    public SseEmitter connect(Long userId) {
        if (!capacity.tryAcquire()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "消息连接繁忙，请稍后重试");
        }
        Connection connection = new Connection(userId, new SseEmitter(CONNECTION_TIMEOUT_MS));
        synchronized (connections) {
            if (stopping) {
                capacity.release();
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "消息服务正在停止");
            }
            Set<Connection> group = connections.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet());
            if (group.size() >= MAX_USER_CONNECTIONS) {
                capacity.release();
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "打开的消息页面过多");
            }
            group.add(connection);
        }
        connection.emitter.onCompletion(() -> remove(connection));
        connection.emitter.onTimeout(() -> close(connection));
        connection.emitter.onError(error -> remove(connection));
        send(connection, "ready", Map.of());
        return connection.emitter;
    }

    public void notifyUsers(Collection<Long> userIds, String event, Long messageId) {
        for (Long userId : userIds) {
            Set<Connection> group = connections.get(userId);
            if (group != null) {
                group.forEach(connection -> send(connection, event,
                        messageId == null ? Map.of() : Map.of("messageId", messageId.toString())));
            }
        }
    }

    /** 每个连接最多一个写任务，慢客户端直接断开，避免堆积占用发送事务。 */
    private void send(Connection connection, String event, Map<String, String> payload) {
        if (connection.closed.get() || !connection.writing.compareAndSet(false, true)) { return; }
        try {
            writers.submit(() -> {
                ScheduledFuture<?> timeout = null;
                try {
                    if (connection.closed.get()) {
                        return;
                    }
                    Thread writer = Thread.currentThread();
                    timeout = scheduler.schedule(() -> {
                        failures.incrementAndGet();
                        writer.interrupt();
                        close(connection);
                    }, SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    connection.emitter.send(SseEmitter.event().name(event).data(payload));
                } catch (IOException | RuntimeException exception) {
                    failures.incrementAndGet();
                    close(connection);
                } finally {
                    if (timeout != null) {
                        timeout.cancel(false);
                    }
                    connection.writing.set(false);
                }
            });
        } catch (RejectedExecutionException exception) {
            close(connection);
        }
    }

    private boolean remove(Connection connection) {
        if (!connection.closed.compareAndSet(false, true)) {
            return false;
        }

        synchronized (connections) {
            Set<Connection> group = connections.get(connection.userId);
            if (group != null) {
                group.remove(connection);
                if (group.isEmpty()) {
                    connections.remove(connection.userId);
                }
            }
            capacity.release();
        }
        return true;
    }

    private void close(Connection connection) {
        if (!remove(connection)) {
            return;
        }
        try {
            writers.execute(() -> complete(connection));
        } catch (RejectedExecutionException exception) {
            LOG.debug("SSE 执行器已关闭", exception);
        }
    }

    private void complete(Connection connection) {
        try {
            connection.emitter.complete();
        } catch (RuntimeException exception) {
            LOG.debug("关闭 SSE 连接失败", exception);
        }
    }

    @PreDestroy
    public void shutdown() {
        synchronized (connections) {
            stopping = true;
        }
        try {
            connections.values().forEach(group -> group.forEach(this::close));
        } finally {
            scheduler.shutdownNow();
            writers.shutdown();
        }
    }

    private static final class Connection {
        private final Long userId;
        private final SseEmitter emitter;
        private final AtomicBoolean writing = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private Connection(Long userId, SseEmitter emitter) { this.userId = userId; this.emitter = emitter; }
    }
}
