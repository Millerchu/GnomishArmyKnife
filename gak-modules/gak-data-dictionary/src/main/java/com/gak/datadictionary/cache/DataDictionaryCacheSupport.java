package com.gak.datadictionary.cache;

import com.gak.framework.dictionary.vo.DictionaryUsageBindingVO;
import java.util.function.Supplier;

/**
 * 数据字典缓存支撑。
 */
public interface DataDictionaryCacheSupport {

    DictionarySnapshot getOrLoadDictionary(String dictCode, Supplier<DictionarySnapshot> loader);

    DictionaryUsageBindingVO getOrLoadUsageBinding(String cacheKey, Supplier<DictionaryUsageBindingVO> loader);

    void evictDictionary(String dictCode);

    void evictAllUsageBindings();
}
