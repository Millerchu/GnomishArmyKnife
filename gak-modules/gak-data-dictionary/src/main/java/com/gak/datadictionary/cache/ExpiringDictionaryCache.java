package com.gak.datadictionary.cache;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** 有界字典缓存，加载数据库时不持有缓存锁。 */
final class ExpiringDictionaryCache<V> {
    private static final int MAX_ENTRIES = 2048;
    private static final long TTL_NANOS = Duration.ofMinutes(2).toNanos();
    private final Map<String, Entry<V>> entries = new LinkedHashMap<>(16, 0.75f, true);

    V get(String key, Supplier<V> loader) {
        synchronized (entries) {
            V value = freshValue(key);
            if (value != null) {
                return value;
            }
        }
        V loaded = loader.get();
        synchronized (entries) {
            V current = freshValue(key);
            if (current != null) {
                return current;
            }
            if (loaded != null) {
                if (entries.size() >= MAX_ENTRIES) {
                    entries.remove(entries.keySet().iterator().next());
                }
                entries.put(key, new Entry<>(loaded, System.nanoTime() + TTL_NANOS));
            }
            return loaded;
        }
    }

    private V freshValue(String key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (System.nanoTime() - entry.expiresAt() >= 0) {
            entries.remove(key);
            return null;
        }
        return entry.value();
    }

    private static record Entry<V>(V value, long expiresAt) {
    }
}
