package com.gak.datamigration.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.service.DataMigrationArchiveService;
import com.gak.datamigration.service.DataMigrationBeanMergeSupport;
import com.gak.datamigration.service.DataMigrationQuerySupport;
import com.gak.datamigration.service.AttachmentMigrationSupport;
import com.gak.datamigration.service.AttachmentMigrationSupport.ExportBundle;
import com.gak.datamigration.service.AttachmentMigrationSupport.TransferItem;
import com.gak.attachment.constant.AttachmentConstants;
import com.gak.framework.exception.BusinessException;
import com.gak.knowledgebase.domain.KnowledgeEntry;
import com.gak.knowledgebase.mapper.KnowledgeEntryMapper;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 经验库迁移处理器。
 */
@Service
public class KnowledgeBaseMigrationHandler implements MigrationResourceHandler {

    private final KnowledgeEntryMapper knowledgeEntryMapper;
    private final UserMapper userMapper;
    private final DataMigrationArchiveService archiveService;
    private final AttachmentMigrationSupport attachmentMigrationSupport;

    public KnowledgeBaseMigrationHandler(KnowledgeEntryMapper knowledgeEntryMapper,
                                         UserMapper userMapper,
                                         DataMigrationArchiveService archiveService,
                                         AttachmentMigrationSupport attachmentMigrationSupport) {
        this.knowledgeEntryMapper = knowledgeEntryMapper;
        this.userMapper = userMapper;
        this.archiveService = archiveService;
        this.attachmentMigrationSupport = attachmentMigrationSupport;
    }

    @Override
    public String resourceCode() {
        return DataMigrationConstants.APP_KNOWLEDGE_BASE;
    }

    @Override
    public String resourceName() {
        return "经验库";
    }

    @Override
    public String resourceType() {
        return DataMigrationConstants.RESOURCE_TYPE_BUSINESS;
    }

    @Override
    public boolean attachmentSupported() {
        return true;
    }

    @Override
    public String entryPath() {
        return "business/" + resourceCode() + "/data.json";
    }

    @Override
    public int order() {
        return 150;
    }

    @Override
    public MigrationResourceExportData exportData(ExportContext context) {
        QueryWrapper<KnowledgeEntry> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("owner_user_id").orderByAsc("updated_at").orderByAsc("id");
        List<KnowledgeEntry> entries = knowledgeEntryMapper.selectList(wrapper);
        ExportBundle bundle = context.includeAttachments()
                ? attachmentMigrationSupport.collect(AttachmentConstants.BUSINESS_KNOWLEDGE_ENTRY,
                entries.stream().map(KnowledgeEntry::getId).toList(), "attachments/knowledge-base")
                : new ExportBundle(List.of(), List.of());
        return new MigrationResourceExportData(resourceCode(), entryPath(), new Payload(entries, bundle.items()),
                entries.size(), bundle.files().size(), bundle.files());
    }

    @Override
    @Transactional
    public MigrationResourceImportResult importData(ImportContext context) throws Exception {
        Payload payload = archiveService.readJson(context.packageRoot(), entryPath(), Payload.class);
        long importedCount = 0L;
        Map<Long, Long> entryIdMappings = new LinkedHashMap<>();
        for (KnowledgeEntry source : DataMigrationQuerySupport.emptyIfNull(payload.getEntries())) {
            if (source == null) {
                continue;
            }
            Long sourceEntryId = source.getId();
            Long targetUserId = resolveUserId(source.getOwnerUserId(), context);
            KnowledgeEntry existing = findExisting(source, targetUserId);
            source.setOwnerUserId(targetUserId);
            source.setReviewedBy(resolveOptionalUserId(source.getReviewedBy(), context));
            if (existing != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_KNOWLEDGE_CONFLICT", "经验条目已存在: " + source.getId());
                }
                if (context.isOverwrite()) {
                    DataMigrationBeanMergeSupport.overwriteNewest(source, existing, "id", "ownerUserId");
                } else {
                    DataMigrationBeanMergeSupport.mergeNewestNonNull(source, existing, "id", "ownerUserId");
                }
                existing.setOwnerUserId(targetUserId);
                knowledgeEntryMapper.updateById(existing);
                entryIdMappings.put(sourceEntryId, existing.getId());
            } else {
                KnowledgeEntry insertEntry = copyEntry(source);
                insertEntry.setOwnerUserId(targetUserId);
                knowledgeEntryMapper.insert(insertEntry);
                entryIdMappings.put(sourceEntryId, insertEntry.getId());
            }
            importedCount++;
        }
        long attachmentCount = attachmentMigrationSupport.restore(context,
                AttachmentConstants.BUSINESS_KNOWLEDGE_ENTRY,
                DataMigrationQuerySupport.emptyIfNull(payload.getAttachments()), entryIdMappings,
                targetId -> {
                    KnowledgeEntry entry = knowledgeEntryMapper.selectById(targetId);
                    return entry == null ? null : entry.getOwnerUserId();
                }, 9);
        return MigrationResourceImportResult.success(importedCount, attachmentCount, "经验库导入完成");
    }

    private KnowledgeEntry findExisting(KnowledgeEntry source, Long targetUserId) {
        KnowledgeEntry byId = source.getId() == null ? null : knowledgeEntryMapper.selectById(source.getId());
        if (byId != null) {
            return byId;
        }
        QueryWrapper<KnowledgeEntry> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", targetUserId).eq("title", source.getTitle());
        DataMigrationQuerySupport.eqNullable(wrapper, "category_name", source.getCategoryName());
        DataMigrationQuerySupport.eqNullable(wrapper, "created_at", source.getCreatedAt());
        return knowledgeEntryMapper.selectOne(wrapper);
    }

    private Long resolveUserId(Long sourceUserId, ImportContext context) {
        Long targetUserId = resolveOptionalUserId(sourceUserId, context);
        if (targetUserId == null) {
            throw new BusinessException("DATA_MIGRATION_KNOWLEDGE_USER_MISSING", "经验库依赖的用户不存在: " + sourceUserId);
        }
        return targetUserId;
    }

    private Long resolveOptionalUserId(Long sourceUserId, ImportContext context) {
        Long mappedId = context.mappedUserId(sourceUserId);
        if (mappedId != null) {
            return mappedId;
        }
        User sameUser = sourceUserId == null ? null : userMapper.selectById(sourceUserId);
        return sameUser == null ? null : sameUser.getId();
    }

    private KnowledgeEntry copyEntry(KnowledgeEntry source) {
        KnowledgeEntry entry = new KnowledgeEntry();
        DataMigrationBeanMergeSupport.overwrite(source, entry);
        return entry;
    }

    /**
     * 经验库导出载荷。
     */
    public static class Payload {

        private List<KnowledgeEntry> entries;
        private List<TransferItem> attachments;

        public Payload() {
        }

        public Payload(List<KnowledgeEntry> entries, List<TransferItem> attachments) {
            this.entries = entries;
            this.attachments = attachments;
        }

        public List<KnowledgeEntry> getEntries() {
            return entries;
        }

        public void setEntries(List<KnowledgeEntry> entries) {
            this.entries = entries;
        }

        public List<TransferItem> getAttachments() { return attachments; }

        public void setAttachments(List<TransferItem> attachments) { this.attachments = attachments; }
    }
}
