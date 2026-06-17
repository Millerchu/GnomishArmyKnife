package com.gak.datamigration.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.service.DataMigrationArchiveService;
import com.gak.datamigration.service.DataMigrationBeanMergeSupport;
import com.gak.datadictionary.domain.DataDictionary;
import com.gak.datadictionary.domain.DataDictionaryItem;
import com.gak.datadictionary.domain.DataDictionaryUsage;
import com.gak.datadictionary.mapper.DataDictionaryItemMapper;
import com.gak.datadictionary.mapper.DataDictionaryMapper;
import com.gak.datadictionary.mapper.DataDictionaryUsageMapper;
import com.gak.framework.exception.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 数据字典迁移处理器。
 */
@Service
public class SystemDictionariesMigrationHandler implements MigrationResourceHandler {

    private final DataDictionaryMapper dictionaryMapper;
    private final DataDictionaryItemMapper itemMapper;
    private final DataDictionaryUsageMapper usageMapper;
    private final DataMigrationArchiveService archiveService;

    public SystemDictionariesMigrationHandler(DataDictionaryMapper dictionaryMapper,
                                              DataDictionaryItemMapper itemMapper,
                                              DataDictionaryUsageMapper usageMapper,
                                              DataMigrationArchiveService archiveService) {
        this.dictionaryMapper = dictionaryMapper;
        this.itemMapper = itemMapper;
        this.usageMapper = usageMapper;
        this.archiveService = archiveService;
    }

    @Override
    public String resourceCode() {
        return DataMigrationConstants.SYSTEM_RESOURCE_DICTIONARIES;
    }

    @Override
    public String resourceName() {
        return "数据字典";
    }

    @Override
    public String resourceType() {
        return DataMigrationConstants.RESOURCE_TYPE_SYSTEM;
    }

    @Override
    public boolean attachmentSupported() {
        return false;
    }

    @Override
    public String entryPath() {
        return "system/dictionaries.json";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public MigrationResourceExportData exportData(ExportContext context) {
        QueryWrapper<DataDictionary> dictionaryWrapper = new QueryWrapper<>();
        dictionaryWrapper.eq("deleted", false).orderByAsc("created_at").orderByAsc("id");
        List<DataDictionary> dictionaries = dictionaryMapper.selectList(dictionaryWrapper);

        QueryWrapper<DataDictionaryItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.eq("deleted", false).orderByAsc("dictionary_id").orderByAsc("sort_no").orderByAsc("id");
        List<DataDictionaryItem> items = itemMapper.selectList(itemWrapper);

        QueryWrapper<DataDictionaryUsage> usageWrapper = new QueryWrapper<>();
        usageWrapper.orderByAsc("dict_code").orderByAsc("app_code").orderByAsc("module_code").orderByAsc("biz_field_code");
        List<DataDictionaryUsage> usages = usageMapper.selectList(usageWrapper);

        long recordCount = (long) dictionaries.size() + items.size() + usages.size();
        return new MigrationResourceExportData(resourceCode(), entryPath(), new Payload(dictionaries, items, usages),
                recordCount, 0L, List.of());
    }

    @Override
    @Transactional
    public MigrationResourceImportResult importData(ImportContext context) throws Exception {
        Payload payload = archiveService.readJson(context.packageRoot(), entryPath(), Payload.class);
        long importedCount = 0L;

        for (DataDictionary source : payload.getDictionaries()) {
            if (source == null || !StringUtils.hasText(source.getDictCode())) {
                continue;
            }
            DataDictionary existing = findDictionary(source.getDictCode());
            if (existing != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_DICT_CONFLICT", "字典已存在: " + source.getDictCode());
                }
                if (context.isOverwrite()) {
                    DataMigrationBeanMergeSupport.overwrite(source, existing, "id", "dictCode");
                } else {
                    DataMigrationBeanMergeSupport.mergeNewestNonNull(source, existing, "id", "dictCode");
                }
                existing.setDictCode(source.getDictCode());
                dictionaryMapper.updateById(existing);
                context.mapDictionaryId(source.getId(), existing.getId());
                importedCount++;
                continue;
            }
            DataDictionary insertDictionary = copyDictionary(source);
            if (insertDictionary.getId() != null && dictionaryMapper.selectById(insertDictionary.getId()) != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_DICT_ID_CONFLICT", "字典 ID 冲突: " + insertDictionary.getId());
                }
                insertDictionary.setId(null);
            }
            dictionaryMapper.insert(insertDictionary);
            context.mapDictionaryId(source.getId(), insertDictionary.getId());
            importedCount++;
        }

        for (DataDictionaryItem source : payload.getItems()) {
            if (source == null || !StringUtils.hasText(source.getDictCode()) || !StringUtils.hasText(source.getItemCode())) {
                continue;
            }
            Long targetDictionaryId = context.mappedDictionaryId(source.getDictionaryId());
            if (targetDictionaryId == null) {
                DataDictionary dictionary = findDictionary(source.getDictCode());
                targetDictionaryId = dictionary == null ? null : dictionary.getId();
            }
            if (targetDictionaryId == null) {
                throw new BusinessException("DATA_MIGRATION_DICT_DEPENDENCY_MISSING", "字典项依赖的字典不存在: " + source.getDictCode());
            }
            DataDictionaryItem existing = findItem(source.getDictCode(), source.getItemCode());
            if (existing != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_DICT_ITEM_CONFLICT",
                            "字典项已存在: " + source.getDictCode() + "/" + source.getItemCode());
                }
                source.setDictionaryId(targetDictionaryId);
                if (context.isOverwrite()) {
                    DataMigrationBeanMergeSupport.overwrite(source, existing, "id", "dictCode", "itemCode");
                } else {
                    DataMigrationBeanMergeSupport.mergeNewestNonNull(source, existing, "id", "dictCode", "itemCode");
                }
                existing.setDictCode(source.getDictCode());
                existing.setItemCode(source.getItemCode());
                existing.setDictionaryId(targetDictionaryId);
                itemMapper.updateById(existing);
                importedCount++;
                continue;
            }
            DataDictionaryItem insertItem = copyItem(source);
            insertItem.setDictionaryId(targetDictionaryId);
            if (insertItem.getId() != null && itemMapper.selectById(insertItem.getId()) != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_DICT_ITEM_ID_CONFLICT", "字典项 ID 冲突: " + insertItem.getId());
                }
                insertItem.setId(null);
            }
            itemMapper.insert(insertItem);
            importedCount++;
        }

        for (DataDictionaryUsage source : payload.getUsages()) {
            if (source == null || !StringUtils.hasText(source.getAppCode())
                    || !StringUtils.hasText(source.getModuleCode()) || !StringUtils.hasText(source.getBizFieldCode())) {
                continue;
            }
            DataDictionaryUsage existing = findUsage(source.getAppCode(), source.getModuleCode(), source.getBizFieldCode());
            Long targetDictionaryId = context.mappedDictionaryId(source.getDictionaryId());
            if (targetDictionaryId == null && StringUtils.hasText(source.getDictCode())) {
                DataDictionary dictionary = findDictionary(source.getDictCode());
                targetDictionaryId = dictionary == null ? null : dictionary.getId();
            }
            if (existing != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_DICT_USAGE_CONFLICT",
                            "字典绑定已存在: " + source.getAppCode() + "/" + source.getModuleCode() + "/" + source.getBizFieldCode());
                }
                source.setDictionaryId(targetDictionaryId);
                if (context.isOverwrite()) {
                    DataMigrationBeanMergeSupport.overwrite(source, existing, "id", "appCode", "moduleCode", "bizFieldCode");
                } else {
                    DataMigrationBeanMergeSupport.mergeNewestNonNull(source, existing, "id", "appCode", "moduleCode", "bizFieldCode");
                }
                existing.setDictionaryId(targetDictionaryId);
                usageMapper.updateById(existing);
                importedCount++;
                continue;
            }
            DataDictionaryUsage insertUsage = copyUsage(source);
            insertUsage.setDictionaryId(targetDictionaryId);
            if (insertUsage.getId() != null && usageMapper.selectById(insertUsage.getId()) != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_DICT_USAGE_ID_CONFLICT", "字典绑定 ID 冲突: " + insertUsage.getId());
                }
                insertUsage.setId(null);
            }
            usageMapper.insert(insertUsage);
            importedCount++;
        }

        return MigrationResourceImportResult.success(importedCount, 0L, "数据字典导入完成");
    }

    private DataDictionary findDictionary(String dictCode) {
        QueryWrapper<DataDictionary> wrapper = new QueryWrapper<>();
        wrapper.eq("dict_code", dictCode).eq("deleted", false);
        return dictionaryMapper.selectOne(wrapper);
    }

    private DataDictionaryItem findItem(String dictCode, String itemCode) {
        QueryWrapper<DataDictionaryItem> wrapper = new QueryWrapper<>();
        wrapper.eq("dict_code", dictCode).eq("item_code", itemCode).eq("deleted", false);
        return itemMapper.selectOne(wrapper);
    }

    private DataDictionaryUsage findUsage(String appCode, String moduleCode, String bizFieldCode) {
        QueryWrapper<DataDictionaryUsage> wrapper = new QueryWrapper<>();
        wrapper.eq("app_code", appCode).eq("module_code", moduleCode).eq("biz_field_code", bizFieldCode);
        return usageMapper.selectOne(wrapper);
    }

    private DataDictionary copyDictionary(DataDictionary source) {
        DataDictionary dictionary = new DataDictionary();
        DataMigrationBeanMergeSupport.overwrite(source, dictionary);
        return dictionary;
    }

    private DataDictionaryItem copyItem(DataDictionaryItem source) {
        DataDictionaryItem item = new DataDictionaryItem();
        DataMigrationBeanMergeSupport.overwrite(source, item);
        return item;
    }

    private DataDictionaryUsage copyUsage(DataDictionaryUsage source) {
        DataDictionaryUsage usage = new DataDictionaryUsage();
        DataMigrationBeanMergeSupport.overwrite(source, usage);
        return usage;
    }

    /**
     * 字典导出载荷。
     */
    public static class Payload {

        private List<DataDictionary> dictionaries;
        private List<DataDictionaryItem> items;
        private List<DataDictionaryUsage> usages;

        public Payload() {
        }

        public Payload(List<DataDictionary> dictionaries,
                       List<DataDictionaryItem> items,
                       List<DataDictionaryUsage> usages) {
            this.dictionaries = dictionaries;
            this.items = items;
            this.usages = usages;
        }

        public List<DataDictionary> getDictionaries() {
            return dictionaries;
        }

        public void setDictionaries(List<DataDictionary> dictionaries) {
            this.dictionaries = dictionaries;
        }

        public List<DataDictionaryItem> getItems() {
            return items;
        }

        public void setItems(List<DataDictionaryItem> items) {
            this.items = items;
        }

        public List<DataDictionaryUsage> getUsages() {
            return usages;
        }

        public void setUsages(List<DataDictionaryUsage> usages) {
            this.usages = usages;
        }
    }
}
