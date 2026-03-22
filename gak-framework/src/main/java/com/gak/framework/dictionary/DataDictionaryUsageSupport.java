package com.gak.framework.dictionary;

import com.gak.framework.dictionary.vo.DictionaryOptionVO;
import com.gak.framework.dictionary.vo.DictionaryUsageBindingVO;
import java.util.Collection;
import java.util.List;

/**
 * 按 usage 绑定访问数据字典。
 */
public interface DataDictionaryUsageSupport {

    DictionaryUsageBindingVO getBinding(String appCode, String moduleCode, String bizFieldCode);

    List<DictionaryOptionVO> listEnabledOptionsByUsage(String appCode, String moduleCode, String bizFieldCode);

    void validateByUsage(String appCode, String moduleCode, String bizFieldCode, String value, boolean required);

    void validateMultiValueByUsage(String appCode,
                                   String moduleCode,
                                   String bizFieldCode,
                                   Collection<String> values,
                                   boolean required);

    String normalizeValueByUsage(String appCode, String moduleCode, String bizFieldCode, String value, boolean required);

    List<String> normalizeMultiValueByUsage(String appCode,
                                            String moduleCode,
                                            String bizFieldCode,
                                            Collection<String> values,
                                            boolean required);
}
