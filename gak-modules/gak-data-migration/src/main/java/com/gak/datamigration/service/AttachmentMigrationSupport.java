package com.gak.datamigration.service;

import static com.gak.attachment.constant.AttachmentConstants.STATUS_ACTIVE;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.attachment.domain.Attachment;
import com.gak.attachment.mapper.AttachmentMapper;
import com.gak.attachment.service.AttachmentService;
import com.gak.attachment.vo.AttachmentVO;
import com.gak.datamigration.handler.MigrationResourceHandler.ImportContext;
import com.gak.datamigration.handler.MigrationResourceHandler.MigrationAttachment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 数据迁移处理器复用的统一附件打包和恢复能力。
 */
@Service
public class AttachmentMigrationSupport {

    private final AttachmentMapper attachmentMapper;
    private final AttachmentService attachmentService;
    private final Path storageRoot;

    public AttachmentMigrationSupport(AttachmentMapper attachmentMapper,
                                      AttachmentService attachmentService,
                                      @Value("${gak.attachment.storage-root:./data}") String storageRoot) {
        this.attachmentMapper = attachmentMapper;
        this.attachmentService = attachmentService;
        this.storageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    public ExportBundle collect(String businessType, List<Long> businessIds, String entryBase) {
        if (businessIds == null || businessIds.isEmpty()) return new ExportBundle(List.of(), List.of());
        QueryWrapper<Attachment> wrapper = new QueryWrapper<>();
        wrapper.eq("business_type", businessType).in("business_id", businessIds).eq("status", STATUS_ACTIVE)
                .orderByAsc("business_id").orderByAsc("sort_no").orderByAsc("id");
        List<TransferItem> items = new ArrayList<>();
        List<MigrationAttachment> files = new ArrayList<>();
        for (Attachment attachment : attachmentMapper.selectList(wrapper)) {
            Path source = storageRoot.resolve(attachment.getObjectKey()).normalize();
            if (!source.startsWith(storageRoot) || !Files.isRegularFile(source)) continue;
            String entryPath = entryBase + "/" + attachment.getId() + "/" + attachment.getOriginalFileName();
            items.add(new TransferItem(attachment.getBusinessId(), attachment.getUsageType(),
                    attachment.getOriginalFileName(), entryPath, attachment.getSortNo()));
            files.add(new MigrationAttachment(entryPath, attachment.getOriginalFileName(), source));
        }
        return new ExportBundle(items, files);
    }

    public long restore(ImportContext context,
                        String businessType,
                        List<TransferItem> items,
                        Map<Long, Long> businessIdMappings,
                        Function<Long, Long> ownerResolver,
                        int maxCount) throws IOException {
        if (!context.includeAttachments() || items == null || items.isEmpty()) return 0L;
        Map<BindingKey, List<Long>> attachmentIds = new LinkedHashMap<>();
        for (TransferItem item : items) {
            Long targetBusinessId = businessIdMappings.get(item.getSourceBusinessId());
            if (targetBusinessId == null) continue;
            Long ownerUserId = ownerResolver.apply(targetBusinessId);
            Path file = context.attachmentPath(item.getEntryPath());
            if (ownerUserId == null || !file.startsWith(context.packageRoot()) || !Files.isRegularFile(file)) continue;
            AttachmentVO uploaded = attachmentService.uploadBytes(ownerUserId, Files.readAllBytes(file),
                    item.getOriginalFileName(), item.getUsageType());
            attachmentIds.computeIfAbsent(new BindingKey(targetBusinessId, ownerUserId, item.getUsageType()), key -> new ArrayList<>())
                    .add(uploaded.getId());
        }
        long restored = 0L;
        for (Map.Entry<BindingKey, List<Long>> entry : attachmentIds.entrySet()) {
            BindingKey key = entry.getKey();
            attachmentService.syncBusinessAttachments(key.ownerUserId(), businessType, key.businessId(),
                    key.usageType(), entry.getValue(), maxCount);
            restored += entry.getValue().size();
        }
        return restored;
    }

    public record ExportBundle(List<TransferItem> items, List<MigrationAttachment> files) {
    }

    private record BindingKey(Long businessId, Long ownerUserId, String usageType) {
    }

    public static class TransferItem {
        private Long sourceBusinessId;
        private String usageType;
        private String originalFileName;
        private String entryPath;
        private Integer sortNo;

        public TransferItem() { }

        public TransferItem(Long sourceBusinessId, String usageType, String originalFileName,
                            String entryPath, Integer sortNo) {
            this.sourceBusinessId = sourceBusinessId;
            this.usageType = usageType;
            this.originalFileName = originalFileName;
            this.entryPath = entryPath;
            this.sortNo = sortNo;
        }

        public Long getSourceBusinessId() { return sourceBusinessId; }
        public void setSourceBusinessId(Long sourceBusinessId) { this.sourceBusinessId = sourceBusinessId; }
        public String getUsageType() { return usageType; }
        public void setUsageType(String usageType) { this.usageType = usageType; }
        public String getOriginalFileName() { return originalFileName; }
        public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
        public String getEntryPath() { return entryPath; }
        public void setEntryPath(String entryPath) { this.entryPath = entryPath; }
        public Integer getSortNo() { return sortNo; }
        public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    }
}
