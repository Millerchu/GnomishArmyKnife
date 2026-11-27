package com.gak.attachment.service;

import static com.gak.attachment.constant.AttachmentConstants.STATUS_DELETED;
import static com.gak.attachment.constant.AttachmentConstants.STATUS_PENDING;
import static com.gak.attachment.constant.AttachmentConstants.STATUS_PURGED;
import static com.gak.attachment.constant.AttachmentConstants.STATUS_PURGING;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
    private static final int BATCH_SIZE = 200;
    private static final int MAX_BATCHES = 20;
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
        purge(STATUS_PURGING, null, now);
        purge(STATUS_PENDING, "created_at", now.minusHours(PENDING_RETENTION_HOURS));
        purge(STATUS_DELETED, "deleted_at", now.minusDays(DELETED_RETENTION_DAYS));
    }

    private void purge(String status, String timeColumn, LocalDateTime deadline) {
        Long lastId = null;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            QueryWrapper<Attachment> query = new QueryWrapper<Attachment>().eq("status", status);
            if (timeColumn != null) {
                query.lt(timeColumn, deadline);
            }
            query.gt(lastId != null, "id", lastId).orderByAsc("id").last("LIMIT " + BATCH_SIZE);
            List<Attachment> attachments = attachmentMapper.selectList(query);
            for (Attachment attachment : attachments) {
                purgeFile(attachment, status, timeColumn, deadline);
                lastId = attachment.getId();
            }
            if (attachments.size() < BATCH_SIZE) {
                return;
            }
        }
    }

    private void purgeFile(Attachment attachment, String status, String timeColumn, LocalDateTime deadline) {
        try {
            if (!STATUS_PURGING.equals(status) && attachmentMapper.update(null, new UpdateWrapper<Attachment>()
                    .eq("id", attachment.getId()).eq("status", status).lt(timeColumn, deadline)
                    .set("status", STATUS_PURGING)) == 0) {
                return;
            }
            // PURGING 在文件操作之前提交，失败后保持不可绑定并在下一轮重试。
            attachmentStorage.delete(attachment.getObjectKey());
            attachmentStorage.delete(attachment.getThumbnailKey());
            attachmentMapper.update(null, new UpdateWrapper<Attachment>()
                    .eq("id", attachment.getId()).eq("status", STATUS_PURGING)
                    .set("status", STATUS_PURGED).set("purged_at", LocalDateTime.now()));
        } catch (RuntimeException exception) {
            LOGGER.warn("附件物理清理失败，attachmentId={}", attachment.getId(), exception);
        }
    }
}
