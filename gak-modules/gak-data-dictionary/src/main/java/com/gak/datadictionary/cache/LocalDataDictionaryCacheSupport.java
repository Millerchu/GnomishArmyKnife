package com.gak.datadictionary.cache;

import com.gak.framework.dictionary.vo.DictionaryOptionVO;
import com.gak.framework.dictionary.vo.DictionaryUsageBindingVO;
import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 单实例字典缓存。写事务提交后切换缓存，隔离仍在加载旧数据的请求。 */
@Component
public class LocalDataDictionaryCacheSupport implements DataDictionaryCacheSupport {

    private volatile ExpiringDictionaryCache<DictionarySnapshot> dictionaryCache = new ExpiringDictionaryCache<>();
    private volatile ExpiringDictionaryCache<DictionaryUsageBindingVO> usageBindingCache = new ExpiringDictionaryCache<>();

    @Override
    public DictionarySnapshot getOrLoadDictionary(String dictCode, Supplier<DictionarySnapshot> loader) {
        DictionarySnapshot snapshot = TransactionSynchronizationManager.isActualTransactionActive()
                ? loader.get() : dictionaryCache.get(normalizeKey(dictCode), loader);
        if (snapshot == null) {
            return null;
        }
        return new DictionarySnapshot(snapshot.dictCode(), snapshot.enabled(), snapshot.allOptions().stream()
                .map(option -> {
                    DictionaryOptionVO copy = new DictionaryOptionVO();
                    BeanUtils.copyProperties(option, copy);
                    return copy;
                }).toList());
    }

    @Override
    public DictionaryUsageBindingVO getOrLoadUsageBinding(String cacheKey, Supplier<DictionaryUsageBindingVO> loader) {
        DictionaryUsageBindingVO binding = TransactionSynchronizationManager.isActualTransactionActive()
                ? loader.get() : usageBindingCache.get(normalizeKey(cacheKey), loader);
        if (binding == null) {
            return null;
        }
        DictionaryUsageBindingVO copy = new DictionaryUsageBindingVO();
        BeanUtils.copyProperties(binding, copy);
        return copy;
    }

    @Override
    public void evictDictionary(String dictCode) {
        afterCommit(() -> dictionaryCache = new ExpiringDictionaryCache<>());
    }

    @Override
    public void evictAllUsageBindings() {
        afterCommit(() -> usageBindingCache = new ExpiringDictionaryCache<>());
    }

    @Override
    public void evictAll() {
        afterCommit(() -> {
            dictionaryCache = new ExpiringDictionaryCache<>();
            usageBindingCache = new ExpiringDictionaryCache<>();
        });
    }

    private void afterCommit(Runnable invalidate) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            invalidate.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invalidate.run();
            }
        });
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
