package com.gak.datadictionary.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.datadictionary.cache.DataDictionaryCacheSupport;
import com.gak.datadictionary.cache.DictionarySnapshot;
import com.gak.datadictionary.domain.DataDictionary;
import com.gak.datadictionary.domain.DataDictionaryItem;
import com.gak.datadictionary.enums.DataDictionaryStatus;
import com.gak.datadictionary.mapper.DataDictionaryItemMapper;
import com.gak.datadictionary.mapper.DataDictionaryMapper;
import com.gak.framework.dictionary.DataDictionarySupport;
import com.gak.framework.dictionary.vo.DictionaryOptionVO;
import com.gak.framework.exception.BusinessException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 数据字典公共支撑实现。
 */
@Component
public class DataDictionarySupportImpl implements DataDictionarySupport {

    private final DataDictionaryMapper dataDictionaryMapper;
    private final DataDictionaryItemMapper dataDictionaryItemMapper;
    private final DataDictionaryCacheSupport dataDictionaryCacheSupport;

    public DataDictionarySupportImpl(DataDictionaryMapper dataDictionaryMapper,
                                     DataDictionaryItemMapper dataDictionaryItemMapper,
                                     DataDictionaryCacheSupport dataDictionaryCacheSupport) {
        this.dataDictionaryMapper = dataDictionaryMapper;
        this.dataDictionaryItemMapper = dataDictionaryItemMapper;
        this.dataDictionaryCacheSupport = dataDictionaryCacheSupport;
    }

    @Override
    public List<DictionaryOptionVO> listEnabledOptions(String dictCode) {
        DictionarySnapshot snapshot = loadSnapshot(dictCode);
        ensureDictionaryEnabled(snapshot);
        List<DictionaryOptionVO> result = new ArrayList<>();
        for (DictionaryOptionVO option : snapshot.allOptions()) {
            result.add(copyOption(option));
        }
        return result;
    }

    @Override
    public void validateItemValue(String dictCode, String value, boolean required) {
        normalizeItemValue(dictCode, value, required);
    }

    @Override
    public void validateItemCode(String dictCode, String code, boolean required) {
        normalizeItemCode(dictCode, code, required);
    }

    @Override
    public String normalizeItemValue(String dictCode, String value, boolean required) {
        String normalizedValue = trimToNull(value);
        if (normalizedValue == null) {
            if (required) {
                throw new BusinessException("DICT_ITEM_VALUE_REQUIRED", "字典值不能为空");
            }
            return null;
        }

        DictionarySnapshot snapshot = loadSnapshot(dictCode);
        ensureDictionaryEnabled(snapshot);
        for (DictionaryOptionVO option : snapshot.allOptions()) {
            if (equalsIgnoreCase(option.getItemValue(), normalizedValue)) {
                return option.getItemValue();
            }
        }
        throw new BusinessException("DICT_ITEM_VALUE_INVALID", "字典值非法: " + normalizedValue);
    }

    @Override
    public String normalizeItemCode(String dictCode, String code, boolean required) {
        String normalizedCode = trimToNull(code);
        if (normalizedCode == null) {
            if (required) {
                throw new BusinessException("DICT_ITEM_CODE_REQUIRED", "字典编码不能为空");
            }
            return null;
        }

        DictionarySnapshot snapshot = loadSnapshot(dictCode);
        ensureDictionaryEnabled(snapshot);
        for (DictionaryOptionVO option : snapshot.allOptions()) {
            if (equalsIgnoreCase(option.getItemCode(), normalizedCode)) {
                return option.getItemCode();
            }
        }
        throw new BusinessException("DICT_ITEM_CODE_INVALID", "字典编码非法: " + normalizedCode);
    }

    @Override
    public String getLabelByValue(String dictCode, String value) {
        String normalizedValue = trimToNull(value);
        if (normalizedValue == null) {
            return value;
        }
        try {
            DictionarySnapshot snapshot = loadSnapshot(dictCode);
            for (DictionaryOptionVO option : snapshot.allOptions()) {
                if (equalsIgnoreCase(option.getItemValue(), normalizedValue)) {
                    return option.getItemLabel();
                }
            }
            return value;
        } catch (BusinessException exception) {
            return value;
        }
    }

    private DictionarySnapshot loadSnapshot(String dictCode) {
        String normalizedDictCode = normalizeDictCode(dictCode);
        return dataDictionaryCacheSupport.getOrLoadDictionary(normalizedDictCode, () -> loadSnapshotFromDatabase(normalizedDictCode));
    }

    private DictionarySnapshot loadSnapshotFromDatabase(String dictCode) {
        QueryWrapper<DataDictionary> dictionaryWrapper = new QueryWrapper<>();
        dictionaryWrapper.eq("dict_code", dictCode).eq("deleted", false);
        DataDictionary dictionary = dataDictionaryMapper.selectOne(dictionaryWrapper);
        if (dictionary == null) {
            throw new BusinessException("DICT_NOT_FOUND", "数据字典不存在: " + dictCode);
        }

        QueryWrapper<DataDictionaryItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.eq("dictionary_id", dictionary.getId()).eq("deleted", false);
        itemWrapper.orderByAsc("sort_no").orderByAsc("id");
        List<DataDictionaryItem> items = dataDictionaryItemMapper.selectList(itemWrapper);

        List<DictionaryOptionVO> enabledOptions = new ArrayList<>();
        for (DataDictionaryItem item : items) {
            if (DataDictionaryStatus.ENABLED.name().equalsIgnoreCase(item.getStatus())) {
                enabledOptions.add(toOptionVO(item));
            }
        }
        enabledOptions.sort(Comparator.comparing(DictionaryOptionVO::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(DictionaryOptionVO::getItemValue, Comparator.nullsLast(String::compareTo)));

        return new DictionarySnapshot(
                dictCode,
                DataDictionaryStatus.ENABLED.name().equalsIgnoreCase(dictionary.getStatus()),
                Collections.unmodifiableList(enabledOptions)
        );
    }

    private void ensureDictionaryEnabled(DictionarySnapshot snapshot) {
        if (!snapshot.enabled()) {
            throw new BusinessException("DICT_DISABLED", "数据字典已禁用: " + snapshot.dictCode());
        }
    }

    private DictionaryOptionVO toOptionVO(DataDictionaryItem item) {
        DictionaryOptionVO vo = new DictionaryOptionVO();
        vo.setItemCode(item.getItemCode());
        vo.setItemLabel(item.getItemLabel());
        vo.setItemValue(item.getItemValue());
        vo.setExtraJson(item.getExtraJson());
        vo.setIsDefault(Boolean.TRUE.equals(item.getIsDefault()));
        vo.setSort(item.getSortNo());
        return vo;
    }

    private DictionaryOptionVO copyOption(DictionaryOptionVO option) {
        DictionaryOptionVO copy = new DictionaryOptionVO();
        copy.setItemCode(option.getItemCode());
        copy.setItemLabel(option.getItemLabel());
        copy.setItemValue(option.getItemValue());
        copy.setExtraJson(option.getExtraJson());
        copy.setIsDefault(option.getIsDefault());
        copy.setSort(option.getSort());
        return copy;
    }

    private String normalizeDictCode(String dictCode) {
        String normalized = trimToNull(dictCode);
        if (normalized == null) {
            throw new BusinessException("DICT_CODE_REQUIRED", "dictCode 不能为空");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
