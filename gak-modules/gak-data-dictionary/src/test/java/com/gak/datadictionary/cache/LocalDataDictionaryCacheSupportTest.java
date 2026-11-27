package com.gak.datadictionary.cache;

import com.gak.framework.dictionary.vo.DictionaryUsageBindingVO;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalDataDictionaryCacheSupportTest {
    private final LocalDataDictionaryCacheSupport cache = new LocalDataDictionaryCacheSupport();

    @Test
    void doesNotExposeMutableBindingAndEvictsOnCommit() {
        String key = "APP|MODULE|field";
        var loaded = cache.getOrLoadUsageBinding(key, () -> binding("OLD"));
        loaded.setDictCode("MUTATED");
        assertEquals("OLD", cache.getOrLoadUsageBinding(key, () -> binding("NEW")).getDictCode());

        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evictAll();
            assertEquals("OLD", cache.getOrLoadUsageBinding(key, () -> binding("NEW")).getDictCode());
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            assertEquals("NEW", cache.getOrLoadUsageBinding(key, () -> binding("NEW")).getDictCode());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private DictionaryUsageBindingVO binding(String dictCode) {
        var binding = new DictionaryUsageBindingVO();
        binding.setDictCode(dictCode);
        return binding;
    }
}
