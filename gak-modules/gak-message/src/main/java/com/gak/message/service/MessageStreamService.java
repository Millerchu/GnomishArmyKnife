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
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService writers = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore capacity = new Semaphore(MAX_CONNECTIONS);
    private final AtomicLong failures = new AtomicLong();

    public MessageStreamService() {
        scheduler.scheduleAtFixedRate(() -> {
            connections.values().forEach(group -> group.forEach(connection -> send(connection, "heartbeat", Map.of())));
        }, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(() -> LOG.info("消息连接数={}, 推送失败数={}",
                MAX_CONNECTIONS - capacity.availablePermits(), failures.get()),
                METRIC_INTERVAL_SECONDS, METRIC_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public SseEmitter connect(Long userId) {
        if (!capacity.tryAcquire()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "消息连接繁忙，请稍后重试");
        }
        Connection connection = new Connection(userId, new SseEmitter(CONNECTION_TIMEOUT_MS));
        synchronized (connections) {
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
                ScheduledFuture<?> timeout = scheduler.schedule(() -> {
                    failures.incrementAndGet();
                    close(connection);
                }, SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                try {
                    connection.emitter.send(SseEmitter.event().name(event).data(payload));
                } catch (IOException | RuntimeException exception) {
                    failures.incrementAndGet();
                    close(connection);
                } finally {
                    timeout.cancel(false);
                    connection.writing.set(false);
                }
            });
        } catch (RejectedExecutionException exception) {
            close(connection);
        }
    }

    private void remove(Connection connection) {
        if (connection.closed.compareAndSet(false, true)) {
            synchronized (connections) {
                Set<Connection> group = connections.get(connection.userId);
                if (group != null) {
                    group.remove(connection);
                    if (group.isEmpty()) { connections.remove(connection.userId); }
                }
                capacity.release();
            }
        }
    }

    private void close(Connection connection) {
        remove(connection);
        connection.emitter.complete();
    }

    @PreDestroy
    public void shutdown() {
        connections.values().forEach(group -> group.forEach(this::close));
        scheduler.shutdownNow();
        writers.shutdownNow();
    }

    private static final class Connection {
        private final Long userId;
        private final SseEmitter emitter;
        private final AtomicBoolean writing = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private Connection(Long userId, SseEmitter emitter) { this.userId = userId; this.emitter = emitter; }
    }
}
