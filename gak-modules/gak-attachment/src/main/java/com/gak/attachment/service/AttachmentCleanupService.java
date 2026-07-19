package com.gak.attachment.service;

import static com.gak.attachment.constant.AttachmentConstants.STATUS_DELETED;
import static com.gak.attachment.constant.AttachmentConstants.STATUS_PENDING;
import static com.gak.attachment.constant.AttachmentConstants.STATUS_PURGED;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.attachment.domain.Attachment;
import com.gak.attachment.mapper.AttachmentMapper;
import com.gak.attachment.storage.AttachmentStorage;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 延迟物理清理避免业务事务和 NAS 操作之间出现不可恢复的不一致。
 */
@Service
public class AttachmentCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentCleanupService.class);
    private static final int PENDING_RETENTION_HOURS = 24;
    private static final int DELETED_RETENTION_DAYS = 7;

    private final AttachmentMapper attachmentMapper;
    private final AttachmentStorage attachmentStorage;

    public AttachmentCleanupService(AttachmentMapper attachmentMapper, AttachmentStorage attachmentStorage) {
        this.attachmentMapper = attachmentMapper;
        this.attachmentStorage = attachmentStorage;
    }

    @Scheduled(cron = "${gak.attachment.cleanup-cron:0 30 3 * * *}")
    public void cleanExpiredFiles() {
        LocalDateTime now = LocalDateTime.now();
        purge(listExpired(STATUS_PENDING, "created_at", now.minusHours(PENDING_RETENTION_HOURS)), now);
        purge(listExpired(STATUS_DELETED, "deleted_at", now.minusDays(DELETED_RETENTION_DAYS)), now);
    }

    private List<Attachment> listExpired(String status, String timeColumn, LocalDateTime deadline) {
        QueryWrapper<Attachment> wrapper = new QueryWrapper<>();
        wrapper.eq("status", status).lt(timeColumn, deadline).orderByAsc(timeColumn).last("LIMIT 200");
        return attachmentMapper.selectList(wrapper);
    }

    private void purge(List<Attachment> attachments, LocalDateTime now) {
        for (Attachment attachment : attachments) {
            try {
                attachmentStorage.delete(attachment.getObjectKey());
                attachmentStorage.delete(attachment.getThumbnailKey());
                attachment.setStatus(STATUS_PURGED);
                attachment.setPurgedAt(now);
                attachmentMapper.updateById(attachment);
            } catch (RuntimeException exception) {
                LOGGER.warn("附件物理清理失败，attachmentId={}", attachment.getId(), exception);
            }
        }
    }
}
