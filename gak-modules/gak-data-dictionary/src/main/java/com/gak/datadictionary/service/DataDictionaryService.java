package com.gak.datadictionary.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.datadictionary.cache.DataDictionaryCacheSupport;
import com.gak.datadictionary.domain.DataDictionary;
import com.gak.datadictionary.domain.DataDictionaryItem;
import com.gak.datadictionary.domain.DataDictionaryUsage;
import com.gak.datadictionary.dto.DataDictionaryQueryRequest;
import com.gak.datadictionary.dto.SaveDataDictionaryItemRequest;
import com.gak.datadictionary.dto.SaveDataDictionaryRequest;
import com.gak.datadictionary.dto.UpdateDataDictionaryItemStatusRequest;
import com.gak.datadictionary.dto.UpdateDataDictionaryStatusRequest;
import com.gak.datadictionary.enums.DataDictionaryScope;
import com.gak.datadictionary.enums.DataDictionaryStatus;
import com.gak.datadictionary.mapper.DataDictionaryItemMapper;
import com.gak.datadictionary.mapper.DataDictionaryMapper;
import com.gak.datadictionary.mapper.DataDictionaryUsageMapper;
import com.gak.datadictionary.vo.DataDictionaryItemListVO;
import com.gak.datadictionary.vo.DataDictionaryItemVO;
import com.gak.datadictionary.vo.DataDictionaryVO;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.PagedResult;
import com.gak.user.constant.UserSecurityConstants;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 数据字典服务。
 */
@Service
public class DataDictionaryService {

    private static final Pattern DICT_CODE_PATTERN = Pattern.compile("^[A-Z0-9_]{2,64}$");

    private final DataDictionaryMapper dataDictionaryMapper;
    private final DataDictionaryItemMapper dataDictionaryItemMapper;
    private final DataDictionaryUsageMapper dataDictionaryUsageMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final DataDictionaryCacheSupport dataDictionaryCacheSupport;

    public DataDictionaryService(DataDictionaryMapper dataDictionaryMapper,
                                 DataDictionaryItemMapper dataDictionaryItemMapper,
                                 DataDictionaryUsageMapper dataDictionaryUsageMapper,
                                 UserMapper userMapper,
                                 ObjectMapper objectMapper,
                                 DataDictionaryCacheSupport dataDictionaryCacheSupport) {
        this.dataDictionaryMapper = dataDictionaryMapper;
        this.dataDictionaryItemMapper = dataDictionaryItemMapper;
        this.dataDictionaryUsageMapper = dataDictionaryUsageMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
        this.dataDictionaryCacheSupport = dataDictionaryCacheSupport;
    }

    public PagedResult<DataDictionaryVO> page(Long currentUserId, DataDictionaryQueryRequest request) {
        requireAdminUser(currentUserId);

        QueryWrapper<DataDictionary> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", false);
        String keyword = trimToNull(request.getKeyword());
        if (keyword != null) {
            wrapper.and(query -> query.like("dict_code", keyword)
                    .or()
                    .like("dict_name", keyword)
                    .or()
                    .like("reference_apps_json", keyword));
        }
        String status = normalizeOptionalStatus(request.getStatus(), "DICT_STATUS_INVALID");
        if (status != null) {
            wrapper.eq("status", status);
        }
        String dictScope = normalizeOptionalScope(request.getDictScope(), "DICT_SCOPE_INVALID");
        if (dictScope != null) {
            wrapper.eq("dict_scope", dictScope);
        }
        String referenceApp = trimToNull(request.getReferenceApp());
        if (referenceApp != null) {
            wrapper.like("reference_apps_json", referenceApp);
        }
        wrapper.orderByDesc("created_at").orderByDesc("id");

        List<DataDictionary> dictionaries = dataDictionaryMapper.selectList(wrapper);
        long total = dictionaries.size();
        long fromIndex = Math.max((request.getPageNo() - 1) * request.getPageSize(), 0L);
        long toIndex = Math.min(fromIndex + request.getPageSize(), total);
        if (fromIndex >= total) {
            return new PagedResult<>(Collections.emptyList(), total);
        }

        List<DataDictionary> pageDictionaries = dictionaries.subList((int) fromIndex, (int) toIndex);
        Map<Long, Integer> itemCountMap = loadItemCountMap(pageDictionaries);
        List<DataDictionaryVO> list = new ArrayList<>();
        for (DataDictionary dictionary : pageDictionaries) {
            list.add(toDictionaryVO(dictionary, itemCountMap.getOrDefault(dictionary.getId(), 0)));
        }
        return new PagedResult<>(list, total);
    }

    @Transactional
    public DataDictionaryVO create(Long currentUserId, SaveDataDictionaryRequest request) {
        User currentUser = requireAdminUser(currentUserId);
        NormalizedDictionary normalized = normalizeDictionaryRequest(request);
        ensureDictCodeUnique(normalized.dictCode(), null);

        LocalDateTime now = LocalDateTime.now();
        DataDictionary dictionary = new DataDictionary();
        dictionary.setDictCode(normalized.dictCode());
        dictionary.setDictName(normalized.dictName());
        dictionary.setDictScope(normalized.dictScope());
        dictionary.setStatus(normalized.status());
        dictionary.setReferenceAppsJson(writeReferenceApps(normalized.referenceApps()));
        dictionary.setDescription(normalized.description());
        dictionary.setCreatorUserId(currentUser.getId());
        dictionary.setCreatorName(resolveUserDisplayName(currentUser));
        dictionary.setCreatedAt(now);
        dictionary.setUpdatedAt(now);
        dictionary.setDeleted(false);
        dataDictionaryMapper.insert(dictionary);
        dataDictionaryCacheSupport.evictDictionary(dictionary.getDictCode());
        return toDictionaryVO(dictionary, 0);
    }

    @Transactional
    public DataDictionaryVO update(Long currentUserId, Long id, SaveDataDictionaryRequest request) {
        requireAdminUser(currentUserId);
        DataDictionary current = getDictionaryOrThrow(id);
        NormalizedDictionary normalized = normalizeDictionaryRequest(request);
        ensureDictCodeImmutable(current, normalized.dictCode());

        current.setDictName(normalized.dictName());
        current.setDictScope(normalized.dictScope());
        current.setStatus(normalized.status());
        current.setReferenceAppsJson(writeReferenceApps(normalized.referenceApps()));
        current.setDescription(normalized.description());
        current.setUpdatedAt(LocalDateTime.now());
        dataDictionaryMapper.updateById(current);
        dataDictionaryCacheSupport.evictDictionary(current.getDictCode());
        return toDictionaryVO(current, loadItemCount(current.getId()));
    }

    @Transactional
    public void delete(Long currentUserId, Long id) {
        requireAdminUser(currentUserId);
        DataDictionary current = getDictionaryOrThrow(id);
        ensureDictionaryNotUsed(current);

        LocalDateTime now = LocalDateTime.now();
        DataDictionary deletedDictionary = new DataDictionary();
        deletedDictionary.setId(current.getId());
        deletedDictionary.setDeleted(true);
        deletedDictionary.setUpdatedAt(now);
        dataDictionaryMapper.updateById(deletedDictionary);

        DataDictionaryItem deletedItem = new DataDictionaryItem();
        deletedItem.setDeleted(true);
        deletedItem.setUpdatedAt(now);
        UpdateWrapper<DataDictionaryItem> itemWrapper = new UpdateWrapper<>();
        itemWrapper.eq("dictionary_id", current.getId()).eq("deleted", false);
        dataDictionaryItemMapper.update(deletedItem, itemWrapper);
        dataDictionaryCacheSupport.evictDictionary(current.getDictCode());
    }

    @Transactional
    public DataDictionaryVO updateStatus(Long currentUserId, Long id, UpdateDataDictionaryStatusRequest request) {
        requireAdminUser(currentUserId);
        DataDictionary current = getDictionaryOrThrow(id);
        String status = normalizeRequiredStatus(request.getStatus(),
                request.getEnabled(),
                DataDictionaryStatus.ENABLED.name().equals(current.getStatus()),
                "DICT_STATUS_INVALID",
                "DICT_STATUS_MISMATCH");

        DataDictionary updated = new DataDictionary();
        updated.setId(current.getId());
        updated.setStatus(status);
        updated.setUpdatedAt(LocalDateTime.now());
        dataDictionaryMapper.updateById(updated);

        current.setStatus(status);
        current.setUpdatedAt(updated.getUpdatedAt());
        dataDictionaryCacheSupport.evictDictionary(current.getDictCode());
        return toDictionaryVO(current, loadItemCount(current.getId()));
    }

    public DataDictionaryItemListVO listItems(Long currentUserId, Long dictionaryId) {
        requireAdminUser(currentUserId);
        getDictionaryOrThrow(dictionaryId);

        QueryWrapper<DataDictionaryItem> wrapper = new QueryWrapper<>();
        wrapper.eq("dictionary_id", dictionaryId).eq("deleted", false);
        wrapper.orderByAsc("sort_no").orderByAsc("id");
        List<DataDictionaryItem> items = dataDictionaryItemMapper.selectList(wrapper);

        DataDictionaryItemListVO result = new DataDictionaryItemListVO();
        List<DataDictionaryItemVO> list = new ArrayList<>();
        for (DataDictionaryItem item : items) {
            list.add(toItemVO(item));
        }
        result.setList(list);
        return result;
    }

    @Transactional
    public DataDictionaryItemVO createItem(Long currentUserId, Long dictionaryId, SaveDataDictionaryItemRequest request) {
        requireAdminUser(currentUserId);
        DataDictionary dictionary = getDictionaryOrThrow(dictionaryId);
        NormalizedDictionaryItem normalized = normalizeItemRequest(request);
        ensureItemCodeUnique(dictionaryId, normalized.itemCode(), null);
        ensureItemValueUnique(dictionaryId, normalized.itemValue(), null);

        LocalDateTime now = LocalDateTime.now();
        if (normalized.isDefault()) {
            clearDefaultItems(dictionaryId, null, now);
        }

        DataDictionaryItem item = new DataDictionaryItem();
        item.setDictionaryId(dictionaryId);
        item.setDictCode(dictionary.getDictCode());
        applyNormalizedItem(item, normalized);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        item.setDeleted(false);
        dataDictionaryItemMapper.insert(item);
        dataDictionaryCacheSupport.evictDictionary(dictionary.getDictCode());
        return toItemVO(item);
    }

    @Transactional
    public DataDictionaryItemVO updateItem(Long currentUserId,
                                           Long dictionaryId,
                                           Long itemId,
                                           SaveDataDictionaryItemRequest request) {
        requireAdminUser(currentUserId);
        DataDictionary dictionary = getDictionaryOrThrow(dictionaryId);
        DataDictionaryItem current = getItemOrThrow(dictionaryId, itemId);
        NormalizedDictionaryItem normalized = normalizeItemRequest(request);
        ensureItemCodeUnique(dictionaryId, normalized.itemCode(), itemId);
        ensureItemValueUnique(dictionaryId, normalized.itemValue(), itemId);

        LocalDateTime now = LocalDateTime.now();
        if (normalized.isDefault()) {
            clearDefaultItems(dictionaryId, itemId, now);
        }

        current.setDictCode(dictionary.getDictCode());
        applyNormalizedItem(current, normalized);
        current.setUpdatedAt(now);
        dataDictionaryItemMapper.updateById(current);
        dataDictionaryCacheSupport.evictDictionary(dictionary.getDictCode());
        return toItemVO(current);
    }

    @Transactional
    public void deleteItem(Long currentUserId, Long dictionaryId, Long itemId) {
        requireAdminUser(currentUserId);
        getDictionaryOrThrow(dictionaryId);
        DataDictionaryItem current = getItemOrThrow(dictionaryId, itemId);

        DataDictionaryItem deletedItem = new DataDictionaryItem();
        deletedItem.setId(current.getId());
        deletedItem.setDeleted(true);
        deletedItem.setUpdatedAt(LocalDateTime.now());
        dataDictionaryItemMapper.updateById(deletedItem);
        dataDictionaryCacheSupport.evictDictionary(current.getDictCode());
    }

    @Transactional
    public DataDictionaryItemVO updateItemStatus(Long currentUserId,
                                                 Long dictionaryId,
                                                 Long itemId,
                                                 UpdateDataDictionaryItemStatusRequest request) {
        requireAdminUser(currentUserId);
        getDictionaryOrThrow(dictionaryId);
        DataDictionaryItem current = getItemOrThrow(dictionaryId, itemId);
        String status = normalizeRequiredStatus(request.getStatus(),
                request.getEnabled(),
                DataDictionaryStatus.ENABLED.name().equals(current.getStatus()),
                "DICT_ITEM_STATUS_INVALID",
                "DICT_ITEM_STATUS_MISMATCH");

        DataDictionaryItem updated = new DataDictionaryItem();
        updated.setId(current.getId());
        updated.setStatus(status);
        updated.setUpdatedAt(LocalDateTime.now());
        dataDictionaryItemMapper.updateById(updated);

        current.setStatus(status);
        current.setUpdatedAt(updated.getUpdatedAt());
        dataDictionaryCacheSupport.evictDictionary(current.getDictCode());
        return toItemVO(current);
    }

    private Map<Long, Integer> loadItemCountMap(List<DataDictionary> dictionaries) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        if (dictionaries.isEmpty()) {
            return result;
        }
        List<Long> ids = dictionaries.stream().map(DataDictionary::getId).toList();
        QueryWrapper<DataDictionaryItem> wrapper = new QueryWrapper<>();
        wrapper.in("dictionary_id", ids).eq("deleted", false);
        List<DataDictionaryItem> items = dataDictionaryItemMapper.selectList(wrapper);
        for (DataDictionaryItem item : items) {
            result.merge(item.getDictionaryId(), 1, Integer::sum);
        }
        return result;
    }

    private int loadItemCount(Long dictionaryId) {
        QueryWrapper<DataDictionaryItem> wrapper = new QueryWrapper<>();
        wrapper.eq("dictionary_id", dictionaryId).eq("deleted", false);
        Long count = dataDictionaryItemMapper.selectCount(wrapper);
        return count == null ? 0 : count.intValue();
    }

    private DataDictionaryVO toDictionaryVO(DataDictionary dictionary, int itemCount) {
        DataDictionaryVO vo = new DataDictionaryVO();
        vo.setId(dictionary.getId());
        vo.setDictCode(dictionary.getDictCode());
        vo.setDictName(dictionary.getDictName());
        vo.setDictScope(dictionary.getDictScope());
        vo.setStatus(dictionary.getStatus());
        vo.setCreatorName(dictionary.getCreatorName());
        vo.setReferenceApps(readReferenceApps(dictionary.getReferenceAppsJson()));
        vo.setItemCount(itemCount);
        vo.setCreateTime(dictionary.getCreatedAt());
        vo.setDescription(dictionary.getDescription());
        return vo;
    }

    private DataDictionaryItemVO toItemVO(DataDictionaryItem item) {
        DataDictionaryItemVO vo = new DataDictionaryItemVO();
        vo.setId(item.getId());
        vo.setItemCode(item.getItemCode());
        vo.setItemLabel(item.getItemLabel());
        vo.setItemValue(item.getItemValue());
        vo.setSort(item.getSortNo());
        vo.setStatus(item.getStatus());
        vo.setIsDefault(Boolean.TRUE.equals(item.getIsDefault()));
        vo.setDescription(item.getDescription());
        vo.setExtraJson(item.getExtraJson());
        return vo;
    }

    private void applyNormalizedItem(DataDictionaryItem item, NormalizedDictionaryItem normalized) {
        item.setItemCode(normalized.itemCode());
        item.setItemLabel(normalized.itemLabel());
        item.setItemValue(normalized.itemValue());
        item.setSortNo(normalized.sortNo());
        item.setStatus(normalized.status());
        item.setIsDefault(normalized.isDefault());
        item.setDescription(normalized.description());
        item.setExtraJson(normalized.extraJson());
    }

    private void clearDefaultItems(Long dictionaryId, Long excludeId, LocalDateTime now) {
        // 新默认项生效前，需要先把同字典下其他默认项取消，避免出现多个默认值。
        DataDictionaryItem clearEntity = new DataDictionaryItem();
        clearEntity.setIsDefault(false);
        clearEntity.setUpdatedAt(now);
        UpdateWrapper<DataDictionaryItem> wrapper = new UpdateWrapper<>();
        wrapper.eq("dictionary_id", dictionaryId).eq("deleted", false).eq("is_default", true);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        dataDictionaryItemMapper.update(clearEntity, wrapper);
    }

    private NormalizedDictionary normalizeDictionaryRequest(SaveDataDictionaryRequest request) {
        return new NormalizedDictionary(
                normalizeDictCode(request.getDictCode()),
                trimRequired(request.getDictName(), "dictName 不能为空"),
                normalizeRequiredScope(request.getDictScope(), "DICT_SCOPE_INVALID"),
                normalizeRequiredStatus(request.getStatus(), request.getEnabled(), true,
                        "DICT_STATUS_INVALID", "DICT_STATUS_MISMATCH"),
                normalizeReferenceApps(request.getReferenceApps()),
                trimToNull(request.getDescription())
        );
    }

    private NormalizedDictionaryItem normalizeItemRequest(SaveDataDictionaryItemRequest request) {
        return new NormalizedDictionaryItem(
                trimRequired(request.getItemCode(), "itemCode 不能为空"),
                trimRequired(request.getItemLabel(), "itemLabel 不能为空"),
                trimRequired(request.getItemValue(), "itemValue 不能为空"),
                request.getSort() != null ? request.getSort() : 0,
                normalizeRequiredStatus(request.getStatus(), request.getEnabled(), true,
                        "DICT_ITEM_STATUS_INVALID", "DICT_ITEM_STATUS_MISMATCH"),
                Boolean.TRUE.equals(request.getIsDefault()),
                trimToNull(request.getDescription()),
                trimToNull(request.getExtraJson())
        );
    }

    private void ensureDictCodeUnique(String dictCode, Long excludeId) {
        QueryWrapper<DataDictionary> wrapper = new QueryWrapper<>();
        wrapper.eq("dict_code", dictCode).eq("deleted", false);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        Long count = dataDictionaryMapper.selectCount(wrapper);
        if (count != null && count > 0L) {
            throw new BusinessException("DICT_CODE_EXISTS", "dictCode 已存在");
        }
    }

    private void ensureDictCodeImmutable(DataDictionary current, String dictCode) {
        if (!current.getDictCode().equalsIgnoreCase(dictCode)) {
            throw new BusinessException("DICT_CODE_IMMUTABLE", "dictCode 创建后不可修改");
        }
    }

    private void ensureItemCodeUnique(Long dictionaryId, String itemCode, Long excludeId) {
        QueryWrapper<DataDictionaryItem> wrapper = new QueryWrapper<>();
        wrapper.eq("dictionary_id", dictionaryId)
                .eq("item_code", itemCode)
                .eq("deleted", false);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        Long count = dataDictionaryItemMapper.selectCount(wrapper);
        if (count != null && count > 0L) {
            throw new BusinessException("DICT_ITEM_CODE_EXISTS", "itemCode 在同一字典内已存在");
        }
    }

    private void ensureItemValueUnique(Long dictionaryId, String itemValue, Long excludeId) {
        QueryWrapper<DataDictionaryItem> wrapper = new QueryWrapper<>();
        wrapper.eq("dictionary_id", dictionaryId)
                .eq("item_value", itemValue)
                .eq("deleted", false);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        Long count = dataDictionaryItemMapper.selectCount(wrapper);
        if (count != null && count > 0L) {
            throw new BusinessException("DICT_ITEM_VALUE_EXISTS", "itemValue 在同一字典内已存在");
        }
    }

    private void ensureDictionaryNotUsed(DataDictionary dictionary) {
        QueryWrapper<DataDictionaryUsage> wrapper = new QueryWrapper<>();
        wrapper.eq("dict_code", dictionary.getDictCode()).eq("status", DataDictionaryStatus.ENABLED.name());
        Long count = dataDictionaryUsageMapper.selectCount(wrapper);
        if (count != null && count > 0L) {
            throw new BusinessException("DICT_IN_USE", "当前字典仍被业务字段引用，无法删除");
        }
    }

    private DataDictionary getDictionaryOrThrow(Long dictionaryId) {
        QueryWrapper<DataDictionary> wrapper = new QueryWrapper<>();
        wrapper.eq("id", dictionaryId).eq("deleted", false);
        DataDictionary dictionary = dataDictionaryMapper.selectOne(wrapper);
        if (dictionary == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据字典不存在");
        }
        return dictionary;
    }

    private DataDictionaryItem getItemOrThrow(Long dictionaryId, Long itemId) {
        QueryWrapper<DataDictionaryItem> wrapper = new QueryWrapper<>();
        wrapper.eq("id", itemId).eq("dictionary_id", dictionaryId).eq("deleted", false);
        DataDictionaryItem item = dataDictionaryItemMapper.selectOne(wrapper);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据字典项不存在");
        }
        return item;
    }

    private User requireAdminUser(Long currentUserId) {
        User currentUser = userMapper.selectById(currentUserId);
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        if (!UserSecurityConstants.ADMIN_ROLE_CODE.equalsIgnoreCase(currentUser.getRoleCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可操作数据字典");
        }
        return currentUser;
    }

    private String normalizeDictCode(String dictCode) {
        String normalized = trimRequired(dictCode, "dictCode 不能为空").toUpperCase();
        if (!DICT_CODE_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException("DICT_CODE_INVALID", "dictCode 仅支持大写字母、数字和下划线");
        }
        return normalized;
    }

    private String normalizeRequiredStatus(String status,
                                           Boolean enabled,
                                           boolean defaultEnabled,
                                           String invalidCode,
                                           String mismatchCode) {
        DataDictionaryStatus resolvedStatus;
        if (StringUtils.hasText(status)) {
            try {
                resolvedStatus = DataDictionaryStatus.fromCode(status);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(invalidCode, "status 非法");
            }
        } else {
            resolvedStatus = defaultEnabled ? DataDictionaryStatus.ENABLED : DataDictionaryStatus.DISABLED;
        }
        boolean resolvedEnabled = enabled != null ? enabled : resolvedStatus.isEnabled();
        if (resolvedEnabled != resolvedStatus.isEnabled()) {
            throw new BusinessException(mismatchCode, "status 与 enabled 语义不一致");
        }
        return resolvedStatus.name();
    }

    private String normalizeOptionalStatus(String status, String invalidCode) {
        String normalized = trimToNull(status);
        if (normalized == null) {
            return null;
        }
        try {
            return DataDictionaryStatus.fromCode(normalized).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(invalidCode, "status 非法");
        }
    }

    private String normalizeRequiredScope(String dictScope, String invalidCode) {
        String normalized = trimToNull(dictScope);
        if (normalized == null) {
            return DataDictionaryScope.PUBLIC.name();
        }
        try {
            return DataDictionaryScope.fromCode(normalized).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(invalidCode, "dictScope 非法");
        }
    }

    private String normalizeOptionalScope(String dictScope, String invalidCode) {
        String normalized = trimToNull(dictScope);
        if (normalized == null) {
            return null;
        }
        try {
            return DataDictionaryScope.fromCode(normalized).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(invalidCode, "dictScope 非法");
        }
    }

    private List<String> normalizeReferenceApps(List<String> referenceApps) {
        if (referenceApps == null || referenceApps.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String referenceApp : referenceApps) {
            String normalized = trimToNull(referenceApp);
            if (normalized == null) {
                continue;
            }
            result.add(normalized);
        }
        return new ArrayList<>(result);
    }

    private List<String> readReferenceApps(String referenceAppsJson) {
        String normalized = trimToNull(referenceAppsJson);
        if (normalized == null) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(normalized, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException exception) {
            List<String> fallback = new ArrayList<>();
            for (String segment : normalized.split(",")) {
                String value = trimToNull(segment);
                if (value != null) {
                    fallback.add(value);
                }
            }
            return fallback;
        }
    }

    private String writeReferenceApps(List<String> referenceApps) {
        try {
            return objectMapper.writeValueAsString(referenceApps != null ? referenceApps : Collections.emptyList());
        } catch (JsonProcessingException exception) {
            throw new BusinessException("DICT_REFERENCE_APPS_INVALID", "referenceApps 无法序列化");
        }
    }

    private String resolveUserDisplayName(User user) {
        if (StringUtils.hasText(user.getDisplayName())) {
            return user.getDisplayName();
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername();
        }
        return "未知用户";
    }

    private String trimRequired(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException("400", message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record NormalizedDictionary(String dictCode,
                                        String dictName,
                                        String dictScope,
                                        String status,
                                        List<String> referenceApps,
                                        String description) {
    }

    private record NormalizedDictionaryItem(String itemCode,
                                            String itemLabel,
                                            String itemValue,
                                            int sortNo,
                                            String status,
                                            boolean isDefault,
                                            String description,
                                            String extraJson) {
    }
}
