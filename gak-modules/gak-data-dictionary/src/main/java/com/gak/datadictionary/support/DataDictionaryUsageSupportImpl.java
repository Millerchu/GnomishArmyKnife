package com.gak.datadictionary.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.datadictionary.cache.DataDictionaryCacheSupport;
import com.gak.datadictionary.domain.DataDictionaryUsage;
import com.gak.datadictionary.enums.DataDictionaryStatus;
import com.gak.datadictionary.mapper.DataDictionaryUsageMapper;
import com.gak.framework.dictionary.DataDictionarySupport;
import com.gak.framework.dictionary.DataDictionaryUsageSupport;
import com.gak.framework.dictionary.vo.DictionaryOptionVO;
import com.gak.framework.dictionary.vo.DictionaryUsageBindingVO;
import com.gak.framework.exception.BusinessException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 数据字典 usage 绑定支撑实现。
 */
@Component
public class DataDictionaryUsageSupportImpl implements DataDictionaryUsageSupport {

    private static final String VALUE_MODE_ITEM_CODE = "ITEM_CODE";

    private final DataDictionaryUsageMapper dataDictionaryUsageMapper;
    private final DataDictionarySupport dataDictionarySupport;
    private final DataDictionaryCacheSupport dataDictionaryCacheSupport;

    public DataDictionaryUsageSupportImpl(DataDictionaryUsageMapper dataDictionaryUsageMapper,
                                          DataDictionarySupport dataDictionarySupport,
                                          DataDictionaryCacheSupport dataDictionaryCacheSupport) {
        this.dataDictionaryUsageMapper = dataDictionaryUsageMapper;
        this.dataDictionarySupport = dataDictionarySupport;
        this.dataDictionaryCacheSupport = dataDictionaryCacheSupport;
    }

    @Override
    public DictionaryUsageBindingVO getBinding(String appCode, String moduleCode, String bizFieldCode) {
        String cacheKey = buildCacheKey(appCode, moduleCode, bizFieldCode);
        return dataDictionaryCacheSupport.getOrLoadUsageBinding(cacheKey,
                () -> loadBindingFromDatabase(appCode, moduleCode, bizFieldCode));
    }

    @Override
    public List<DictionaryOptionVO> listEnabledOptionsByUsage(String appCode, String moduleCode, String bizFieldCode) {
        DictionaryUsageBindingVO binding = getBinding(appCode, moduleCode, bizFieldCode);
        return dataDictionarySupport.listEnabledOptions(binding.getDictCode());
    }

    @Override
    public void validateByUsage(String appCode, String moduleCode, String bizFieldCode, String value, boolean required) {
        normalizeValueByUsage(appCode, moduleCode, bizFieldCode, value, required);
    }

    @Override
    public void validateMultiValueByUsage(String appCode,
                                          String moduleCode,
                                          String bizFieldCode,
                                          Collection<String> values,
                                          boolean required) {
        normalizeMultiValueByUsage(appCode, moduleCode, bizFieldCode, values, required);
    }

    @Override
    public String normalizeValueByUsage(String appCode, String moduleCode, String bizFieldCode, String value, boolean required) {
        DictionaryUsageBindingVO binding = getBinding(appCode, moduleCode, bizFieldCode);
        if (VALUE_MODE_ITEM_CODE.equalsIgnoreCase(binding.getValueMode())) {
            return dataDictionarySupport.normalizeItemCode(binding.getDictCode(), value, required);
        }
        return dataDictionarySupport.normalizeItemValue(binding.getDictCode(), value, required);
    }

    @Override
    public List<String> normalizeMultiValueByUsage(String appCode,
                                                   String moduleCode,
                                                   String bizFieldCode,
                                                   Collection<String> values,
                                                   boolean required) {
        if (values == null || values.isEmpty()) {
            if (required) {
                throw new BusinessException("DICT_MULTI_VALUE_REQUIRED", "字典多选值不能为空");
            }
            return Collections.emptyList();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            result.add(normalizeValueByUsage(appCode, moduleCode, bizFieldCode, value, true));
        }
        return new ArrayList<>(result);
    }

    private DictionaryUsageBindingVO loadBindingFromDatabase(String appCode, String moduleCode, String bizFieldCode) {
        QueryWrapper<DataDictionaryUsage> wrapper = new QueryWrapper<>();
        wrapper.eq("app_code", normalizeCode(appCode))
                .eq("module_code", normalizeCode(moduleCode))
                .eq("biz_field_code", trimRequired(bizFieldCode, "bizFieldCode 不能为空"))
                .eq("status", DataDictionaryStatus.ENABLED.name());
        DataDictionaryUsage usage = dataDictionaryUsageMapper.selectOne(wrapper);
        if (usage == null) {
            throw new BusinessException("DICT_USAGE_NOT_FOUND", "未找到数据字典绑定: "
                    + trimRequired(appCode, "appCode 不能为空") + "/" + trimRequired(moduleCode, "moduleCode 不能为空")
                    + "/" + trimRequired(bizFieldCode, "bizFieldCode 不能为空"));
        }
        DictionaryUsageBindingVO vo = new DictionaryUsageBindingVO();
        vo.setId(usage.getId());
        vo.setDictCode(usage.getDictCode());
        vo.setDictionaryId(usage.getDictionaryId());
        vo.setAppCode(usage.getAppCode());
        vo.setAppName(usage.getAppName());
        vo.setModuleCode(usage.getModuleCode());
        vo.setModuleName(usage.getModuleName());
        vo.setBizFieldCode(usage.getBizFieldCode());
        vo.setBizFieldName(usage.getBizFieldName());
        vo.setUsageType(usage.getUsageType());
        vo.setValueMode(usage.getValueMode());
        vo.setAllowMultiple(usage.getAllowMultiple());
        vo.setRequiredFlag(usage.getRequiredFlag());
        vo.setStatus(usage.getStatus());
        vo.setUsageCount(usage.getUsageCount());
        vo.setLastUsedAt(usage.getLastUsedAt());
        return vo;
    }

    private String buildCacheKey(String appCode, String moduleCode, String bizFieldCode) {
        return normalizeCode(appCode) + "|" + normalizeCode(moduleCode) + "|" + trimRequired(bizFieldCode, "bizFieldCode 不能为空");
    }

    private String normalizeCode(String value) {
        return trimRequired(value, "编码不能为空").toUpperCase(Locale.ROOT);
    }

    private String trimRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("DICT_USAGE_PARAM_REQUIRED", message);
        }
        return value.trim();
    }
}
