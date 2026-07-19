package com.gak.attachment.service;

import static com.gak.attachment.constant.AttachmentConstants.STATUS_ACTIVE;
import static com.gak.attachment.constant.AttachmentConstants.STATUS_DELETED;
import static com.gak.attachment.constant.AttachmentConstants.STATUS_PENDING;
import static com.gak.attachment.constant.AttachmentConstants.STORAGE_LOCAL_NAS;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.attachment.domain.Attachment;
import com.gak.attachment.mapper.AttachmentMapper;
import com.gak.attachment.policy.AttachmentPolicyRegistry;
import com.gak.attachment.service.AttachmentFileProcessor.ProcessedFile;
import com.gak.attachment.storage.AttachmentStorage;
import com.gak.attachment.vo.AttachmentVO;
import com.gak.framework.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 统一附件业务服务，负责上传、业务绑定、授权读取和软删除。
 */
@Service
public class AttachmentService {

    private static final DateTimeFormatter OBJECT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM");

    private final AttachmentMapper attachmentMapper;
    private final AttachmentStorage attachmentStorage;
    private final AttachmentFileProcessor fileProcessor;
    private final AttachmentPolicyRegistry policyRegistry;

    public AttachmentService(AttachmentMapper attachmentMapper,
                             AttachmentStorage attachmentStorage,
                             AttachmentFileProcessor fileProcessor,
                             AttachmentPolicyRegistry policyRegistry) {
        this.attachmentMapper = attachmentMapper;
        this.attachmentStorage = attachmentStorage;
        this.fileProcessor = fileProcessor;
        this.policyRegistry = policyRegistry;
    }

    public AttachmentVO upload(Long currentUserId, MultipartFile file, String usageType) {
        ProcessedFile processedFile = fileProcessor.process(file, usageType);
        return storePending(currentUserId, usageType, processedFile);
    }

    public AttachmentVO uploadBytes(Long currentUserId, byte[] content, String originalFileName, String usageType) {
        return storePending(currentUserId, usageType, fileProcessor.process(content, originalFileName, usageType));
    }

    private AttachmentVO storePending(Long currentUserId, String usageType, ProcessedFile processedFile) {
        String normalizedUsageType = usageType.trim().toUpperCase(Locale.ROOT);
        String randomName = UUID.randomUUID().toString().replace("-", "");
        String datePath = LocalDate.now().format(OBJECT_DATE_FORMATTER);
        String objectKey = "attachments/" + currentUserId + "/" + datePath + "/" + randomName + processedFile.extension();
        String thumbnailKey = processedFile.thumbnail() == null ? null
                : "attachments/thumbnails/" + currentUserId + "/" + datePath + "/" + randomName
                + (processedFile.contentType().equals("image/png") ? ".png" : ".jpg");

        attachmentStorage.store(objectKey, processedFile.content());
        if (thumbnailKey != null) {
            try {
                attachmentStorage.store(thumbnailKey, processedFile.thumbnail());
            } catch (RuntimeException exception) {
                attachmentStorage.delete(objectKey);
                throw exception;
            }
        }

        Attachment attachment = new Attachment();
        attachment.setOwnerUserId(currentUserId);
        attachment.setUsageType(normalizedUsageType);
        attachment.setOriginalFileName(processedFile.originalFileName());
        attachment.setContentType(processedFile.contentType());
        attachment.setFileExtension(processedFile.extension());
        attachment.setFileSize((long) processedFile.content().length);
        attachment.setSha256(processedFile.sha256());
        attachment.setStorageProvider(STORAGE_LOCAL_NAS);
        attachment.setObjectKey(objectKey);
        attachment.setThumbnailKey(thumbnailKey);
        attachment.setStatus(STATUS_PENDING);
        attachment.setSortNo(0);
        attachment.setCreatedBy(currentUserId);
        attachment.setCreatedAt(LocalDateTime.now());
        try {
            attachmentMapper.insert(attachment);
        } catch (RuntimeException exception) {
            deletePhysicalFilesQuietly(attachment);
            throw exception;
        }
        return toVO(attachment);
    }

    /**
     * 为仍位于旧目录的文件创建统一元数据，物理文件不搬迁且可安全重复执行。
     */
    @Transactional
    public void registerLegacyAttachment(Long ownerUserId,
                                         String businessType,
                                         Long businessId,
                                         String usageType,
                                         String objectKey,
                                         String originalFileName,
                                         String legacySourceKey) {
        QueryWrapper<Attachment> existingWrapper = new QueryWrapper<>();
        existingWrapper.eq("legacy_source_key", legacySourceKey).last("LIMIT 1");
        if (attachmentMapper.selectCount(existingWrapper) > 0 || !attachmentStorage.exists(objectKey)) return;

        Resource resource = attachmentStorage.load(objectKey);
        String extension = resolveLegacyExtension(originalFileName);
        Attachment attachment = new Attachment();
        attachment.setOwnerUserId(ownerUserId);
        attachment.setBusinessType(businessType);
        attachment.setBusinessId(businessId);
        attachment.setUsageType(usageType);
        attachment.setOriginalFileName(originalFileName);
        attachment.setContentType(resolveLegacyContentType(extension));
        attachment.setFileExtension(extension);
        attachment.setStorageProvider(STORAGE_LOCAL_NAS);
        attachment.setObjectKey(objectKey);
        attachment.setStatus(STATUS_ACTIVE);
        attachment.setSortNo(listActiveEntities(businessType, businessId, usageType).size());
        attachment.setCreatedBy(ownerUserId);
        attachment.setCreatedAt(LocalDateTime.now());
        attachment.setBoundAt(LocalDateTime.now());
        attachment.setLegacySourceKey(legacySourceKey);
        try (InputStream inputStream = resource.getInputStream()) {
            byte[] content = inputStream.readAllBytes();
            attachment.setFileSize((long) content.length);
            attachment.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new BusinessException("ATTACHMENT_LEGACY_READ_FAILED", "旧附件读取失败");
        }
        attachmentMapper.insert(attachment);
    }

    /**
     * 用请求中的完整 ID 列表同步业务附件，缺失项将进入延迟清理状态。
     */
    @Transactional
    public List<AttachmentVO> syncBusinessAttachments(Long ownerUserId,
                                                      String businessType,
                                                      Long businessId,
                                                      String usageType,
                                                      List<Long> attachmentIds,
                                                      int maxCount) {
        List<Long> normalizedIds = normalizeIds(attachmentIds, maxCount);
        List<Attachment> submitted = loadSubmittedAttachments(normalizedIds);
        validateSubmittedAttachments(submitted, normalizedIds, ownerUserId, businessType, businessId, usageType);

        List<Attachment> current = listActiveEntities(businessType, businessId, usageType);
        LocalDateTime now = LocalDateTime.now();
        for (Attachment existing : current) {
            if (!normalizedIds.contains(existing.getId())) {
                markDeleted(existing, now);
            }
        }
        for (int index = 0; index < submitted.size(); index++) {
            Attachment attachment = findById(submitted, normalizedIds.get(index));
            attachment.setBusinessType(businessType);
            attachment.setBusinessId(businessId);
            attachment.setStatus(STATUS_ACTIVE);
            attachment.setSortNo(index);
            attachment.setBoundAt(attachment.getBoundAt() == null ? now : attachment.getBoundAt());
            attachment.setDeletedAt(null);
            attachmentMapper.updateById(attachment);
        }
        return listBusinessAttachments(businessType, businessId, usageType);
    }

    public List<AttachmentVO> listBusinessAttachments(String businessType, Long businessId, String usageType) {
        return listActiveEntities(businessType, businessId, usageType).stream().map(this::toVO).toList();
    }

    public List<AttachmentVO> listBusinessAttachments(String businessType, Long businessId) {
        QueryWrapper<Attachment> wrapper = new QueryWrapper<>();
        wrapper.eq("business_type", businessType)
                .eq("business_id", businessId)
                .eq("status", STATUS_ACTIVE)
                .orderByAsc("sort_no").orderByAsc("id");
        return attachmentMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    public ResourceFile loadContent(Long currentUserId, Long attachmentId, boolean thumbnail) {
        Attachment attachment = getReadableAttachment(currentUserId, attachmentId);
        String objectKey = thumbnail ? attachment.getThumbnailKey() : attachment.getObjectKey();
        if (!StringUtils.hasText(objectKey)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件缩略图不存在");
        }
        Resource resource = attachmentStorage.load(objectKey);
        String contentType = thumbnail
                ? (objectKey.endsWith(".png") ? "image/png" : "image/jpeg")
                : attachment.getContentType();
        return new ResourceFile(resource, contentType, attachment.getOriginalFileName(), isInlineContent(contentType));
    }

    @Transactional
    public void delete(Long currentUserId, Long attachmentId) {
        Attachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || STATUS_DELETED.equals(attachment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在");
        }
        if (STATUS_PENDING.equals(attachment.getStatus())) {
            if (!Objects.equals(attachment.getOwnerUserId(), currentUserId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在");
            }
        } else if (!STATUS_ACTIVE.equals(attachment.getStatus())
                || !policyRegistry.canManage(attachment.getBusinessType(), currentUserId, attachment.getBusinessId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在");
        }
        markDeleted(attachment, LocalDateTime.now());
    }

    @Transactional
    public void deleteByBusiness(String businessType, Long businessId) {
        LocalDateTime now = LocalDateTime.now();
        for (Attachment attachment : listActiveEntities(businessType, businessId, null)) {
            markDeleted(attachment, now);
        }
    }

    public AttachmentVO toVO(Attachment attachment) {
        AttachmentVO vo = new AttachmentVO();
        vo.setId(attachment.getId());
        vo.setOriginalFileName(attachment.getOriginalFileName());
        vo.setContentType(attachment.getContentType());
        vo.setFileSize(attachment.getFileSize());
        vo.setUsageType(attachment.getUsageType());
        vo.setSortNo(attachment.getSortNo());
        vo.setImage(attachment.getContentType() != null && attachment.getContentType().startsWith("image/"));
        vo.setThumbnailAvailable(StringUtils.hasText(attachment.getThumbnailKey()));
        return vo;
    }

    private Attachment getReadableAttachment(Long currentUserId, Long attachmentId) {
        Attachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在");
        }
        if (STATUS_PENDING.equals(attachment.getStatus()) && Objects.equals(attachment.getOwnerUserId(), currentUserId)) {
            return attachment;
        }
        if (!STATUS_ACTIVE.equals(attachment.getStatus())
                || !policyRegistry.canView(attachment.getBusinessType(), currentUserId, attachment.getBusinessId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在");
        }
        return attachment;
    }

    private List<Long> normalizeIds(List<Long> ids, int maxCount) {
        if (ids == null || ids.isEmpty()) return List.of();
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null) throw new BusinessException("ATTACHMENT_ID_INVALID", "附件 ID 不能为空");
            distinct.add(id);
        }
        if (distinct.size() != ids.size()) {
            throw new BusinessException("ATTACHMENT_ID_DUPLICATED", "附件列表包含重复文件");
        }
        if (distinct.size() > maxCount) {
            throw new BusinessException("ATTACHMENT_COUNT_EXCEEDED", "附件数量超过允许上限");
        }
        return List.copyOf(distinct);
    }

    private List<Attachment> loadSubmittedAttachments(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        QueryWrapper<Attachment> wrapper = new QueryWrapper<>();
        wrapper.in("id", ids);
        return attachmentMapper.selectList(wrapper);
    }

    private void validateSubmittedAttachments(List<Attachment> attachments,
                                              List<Long> ids,
                                              Long ownerUserId,
                                              String businessType,
                                              Long businessId,
                                              String usageType) {
        if (attachments.size() != ids.size()) {
            throw new BusinessException("ATTACHMENT_NOT_FOUND", "部分附件不存在或已失效");
        }
        for (Attachment attachment : attachments) {
            boolean pendingOwned = STATUS_PENDING.equals(attachment.getStatus())
                    && Objects.equals(ownerUserId, attachment.getOwnerUserId());
            boolean alreadyBound = STATUS_ACTIVE.equals(attachment.getStatus())
                    && Objects.equals(businessType, attachment.getBusinessType())
                    && Objects.equals(businessId, attachment.getBusinessId());
            if ((!pendingOwned && !alreadyBound) || !Objects.equals(usageType, attachment.getUsageType())) {
                throw new BusinessException("ATTACHMENT_BIND_FORBIDDEN", "附件不属于当前用户或用途不匹配");
            }
        }
    }

    private List<Attachment> listActiveEntities(String businessType, Long businessId, String usageType) {
        QueryWrapper<Attachment> wrapper = new QueryWrapper<>();
        wrapper.eq("business_type", businessType)
                .eq("business_id", businessId)
                .eq("status", STATUS_ACTIVE);
        if (StringUtils.hasText(usageType)) wrapper.eq("usage_type", usageType);
        wrapper.orderByAsc("sort_no").orderByAsc("id");
        return attachmentMapper.selectList(wrapper);
    }

    private Attachment findById(List<Attachment> attachments, Long id) {
        return attachments.stream().filter(item -> Objects.equals(item.getId(), id)).findFirst().orElseThrow();
    }

    private void markDeleted(Attachment attachment, LocalDateTime now) {
        attachment.setStatus(STATUS_DELETED);
        attachment.setDeletedAt(now);
        attachmentMapper.updateById(attachment);
    }

    private boolean isInlineContent(String contentType) {
        return contentType != null && (contentType.startsWith("image/") || "application/pdf".equals(contentType));
    }

    private void deletePhysicalFilesQuietly(Attachment attachment) {
        try { attachmentStorage.delete(attachment.getObjectKey()); } catch (RuntimeException ignored) { }
        try { attachmentStorage.delete(attachment.getThumbnailKey()); } catch (RuntimeException ignored) { }
    }

    private String resolveLegacyExtension(String fileName) {
        int dotIndex = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dotIndex < 0 ? ".bin" : fileName.substring(dotIndex).toLowerCase(Locale.ROOT);
    }

    private String resolveLegacyContentType(String extension) {
        return switch (extension) {
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".svg" -> "image/svg+xml";
            case ".pdf" -> "application/pdf";
            case ".doc" -> "application/msword";
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }

    public record ResourceFile(Resource resource, String contentType, String originalFileName, boolean inline) {
    }
}
