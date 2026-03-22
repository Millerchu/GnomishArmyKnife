package com.gak.datadictionary.cache;

import com.gak.framework.dictionary.vo.DictionaryOptionVO;
import java.util.List;

/**
 * 字典快照。
 */
public record DictionarySnapshot(
        String dictCode,
        boolean enabled,
        List<DictionaryOptionVO> allOptions
) {
}
