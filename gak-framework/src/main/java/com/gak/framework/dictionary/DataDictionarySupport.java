package com.gak.framework.dictionary;

import com.gak.framework.dictionary.vo.DictionaryOptionVO;
import java.util.List;

/**
 * 按 dictCode 访问数据字典。
 */
public interface DataDictionarySupport {

    List<DictionaryOptionVO> listEnabledOptions(String dictCode);

    void validateItemValue(String dictCode, String value, boolean required);

    void validateItemCode(String dictCode, String code, boolean required);

    String normalizeItemValue(String dictCode, String value, boolean required);

    String normalizeItemCode(String dictCode, String code, boolean required);

    String getLabelByValue(String dictCode, String value);
}
