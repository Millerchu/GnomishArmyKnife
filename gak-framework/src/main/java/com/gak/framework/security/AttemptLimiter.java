package com.gak.framework.security;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 在验证前占用尝试次数，限制并发试错和记录表的内存占用。 */
public final class AttemptLimiter {
    private static final int MAX_KEYS = 10_000;
    private final Map<String, long[]> attempts = new HashMap<>();
    private final int maxAttempts;
    private final long windowNanos;
    private long nextCleanup = System.nanoTime();

    public AttemptLimiter(int maxAttempts, Duration window) {
        this.maxAttempts = maxAttempts;
        this.windowNanos = window.toNanos();
    }

    public synchronized void acquire(String key) {
        long now = System.nanoTime();
        if (now - nextCleanup >= 0) {
            attempts.values().removeIf(entry -> now - entry[0] >= 0);
            nextCleanup = now + windowNanos;
        }
        long[] entry = attempts.get(key);
        if (entry == null || now - entry[0] >= 0) {
            if (entry == null && attempts.size() >= MAX_KEYS) {
                throw tooManyAttempts();
            }
            entry = new long[]{now + windowNanos, 0};
            attempts.put(key, entry);
        }
        if (entry[1] >= maxAttempts) {
            throw tooManyAttempts();
        }
        entry[1]++;
    }

    private ResponseStatusException tooManyAttempts() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "操作过于频繁，请稍后重试");
    }
}
