package com.gak.datadictionary.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.datadictionary.cache.DataDictionaryCacheSupport;
import com.gak.datadictionary.domain.DataDictionaryItem;
import com.gak.datadictionary.domain.DataDictionaryUsage;
import com.gak.datadictionary.enums.DataDictionaryStatus;
import com.gak.datadictionary.mapper.DataDictionaryItemMapper;
import com.gak.datadictionary.mapper.DataDictionaryUsageMapper;
import com.gak.framework.dictionary.DataDictionarySupport;
import com.gak.framework.dictionary.DataDictionaryUsageSupport;
import com.gak.framework.dictionary.vo.DictionaryOptionVO;
import com.gak.framework.dictionary.vo.DictionaryUsageBindingVO;
import com.gak.framework.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 数据字典 usage 绑定支撑实现。
 */
@Component
public class DataDictionaryUsageSupportImpl implements DataDictionaryUsageSupport {

    private static final String VALUE_MODE_ITEM_CODE = "ITEM_CODE";
    private static final int MAX_OPTION_NAME_LENGTH = 64;

    private final DataDictionaryUsageMapper dataDictionaryUsageMapper;
    private final DataDictionaryItemMapper dataDictionaryItemMapper;
    private final DataDictionarySupport dataDictionarySupport;
    private final DataDictionaryCacheSupport dataDictionaryCacheSupport;

    public DataDictionaryUsageSupportImpl(DataDictionaryUsageMapper dataDictionaryUsageMapper,
                                          DataDictionaryItemMapper dataDictionaryItemMapper,
                                          DataDictionarySupport dataDictionarySupport,
                                          DataDictionaryCacheSupport dataDictionaryCacheSupport) {
        this.dataDictionaryUsageMapper = dataDictionaryUsageMapper;
        this.dataDictionaryItemMapper = dataDictionaryItemMapper;
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
    @Transactional
    public DictionaryOptionVO createEnabledOptionByUsage(String appCode,
                                                         String moduleCode,
                                                         String bizFieldCode,
                                                         String optionName) {
        DictionaryUsageBindingVO binding = getBinding(appCode, moduleCode, bizFieldCode);
        String normalizedName = normalizeOptionName(optionName);
        DataDictionaryItem existingItem = findExistingItem(binding.getDictionaryId(), normalizedName);
        if (existingItem != null) {
            if (!DataDictionaryStatus.ENABLED.name().equals(existingItem.getStatus())) {
                throw new BusinessException("DICT_ITEM_DISABLED", "该选项已存在但未启用，请在数据字典中启用");
            }
            return toOption(existingItem);
        }

        List<DataDictionaryItem> dictionaryItems = listActiveItems(binding.getDictionaryId());
        int nextSortNo = dictionaryItems.stream()
                .map(DataDictionaryItem::getSortNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        LocalDateTime now = LocalDateTime.now();
        DataDictionaryItem newItem = new DataDictionaryItem();
        newItem.setDictionaryId(binding.getDictionaryId());
        newItem.setDictCode(binding.getDictCode());
        newItem.setItemCode("custom_" + UUID.randomUUID().toString().replace("-", ""));
        newItem.setItemLabel(normalizedName);
        newItem.setItemValue(normalizedName);
        newItem.setSortNo(nextSortNo);
        newItem.setStatus(DataDictionaryStatus.ENABLED.name());
        newItem.setIsDefault(false);
        newItem.setDescription("由业务表单就地新增");
        newItem.setCreatedAt(now);
        newItem.setUpdatedAt(now);
        newItem.setDeleted(false);
        dataDictionaryItemMapper.insert(newItem);
        dataDictionaryCacheSupport.evictDictionary(binding.getDictCode());
        return toOption(newItem);
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

    private String normalizeOptionName(String optionName) {
        String normalizedName = trimRequired(optionName, "选项名称不能为空");
        if (normalizedName.length() > MAX_OPTION_NAME_LENGTH) {
            throw new BusinessException("DICT_ITEM_NAME_TOO_LONG", "选项名称长度不能超过 64");
        }
        return normalizedName;
    }

    private DataDictionaryItem findExistingItem(Long dictionaryId, String optionName) {
        return listActiveItems(dictionaryId).stream()
                .filter(item -> optionName.equalsIgnoreCase(item.getItemLabel())
                        || optionName.equalsIgnoreCase(item.getItemValue()))
                .findFirst()
                .orElse(null);
    }

    private List<DataDictionaryItem> listActiveItems(Long dictionaryId) {
        QueryWrapper<DataDictionaryItem> wrapper = new QueryWrapper<>();
        wrapper.eq("dictionary_id", dictionaryId)
                .eq("deleted", false)
                .orderByAsc("sort_no")
                .orderByAsc("id");
        return dataDictionaryItemMapper.selectList(wrapper);
    }

    private DictionaryOptionVO toOption(DataDictionaryItem item) {
        DictionaryOptionVO option = new DictionaryOptionVO();
        option.setItemCode(item.getItemCode());
        option.setItemLabel(item.getItemLabel());
        option.setItemValue(item.getItemValue());
        option.setExtraJson(item.getExtraJson());
        option.setIsDefault(Boolean.TRUE.equals(item.getIsDefault()));
        option.setSort(item.getSortNo());
        return option;
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
