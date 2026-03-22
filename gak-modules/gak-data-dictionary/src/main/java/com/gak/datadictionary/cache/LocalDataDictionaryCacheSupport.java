package com.gak.datadictionary.cache;

import com.gak.framework.dictionary.vo.DictionaryUsageBindingVO;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 单体部署下的本地字典缓存。
 */
@Component
public class LocalDataDictionaryCacheSupport implements DataDictionaryCacheSupport {

    private final Map<String, DictionarySnapshot> dictionaryCache = new ConcurrentHashMap<>();
    private final Map<String, DictionaryUsageBindingVO> usageBindingCache = new ConcurrentHashMap<>();

    @Override
    public DictionarySnapshot getOrLoadDictionary(String dictCode, Supplier<DictionarySnapshot> loader) {
        return dictionaryCache.computeIfAbsent(normalizeKey(dictCode), key -> loader.get());
    }

    @Override
    public DictionaryUsageBindingVO getOrLoadUsageBinding(String cacheKey, Supplier<DictionaryUsageBindingVO> loader) {
        return usageBindingCache.computeIfAbsent(normalizeKey(cacheKey), key -> loader.get());
    }

    @Override
    public void evictDictionary(String dictCode) {
        dictionaryCache.remove(normalizeKey(dictCode));
    }

    @Override
    public void evictAllUsageBindings() {
        usageBindingCache.clear();
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
