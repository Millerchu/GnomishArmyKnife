package com.gak.attachment.storage;

import org.springframework.core.io.Resource;

/**
 * 附件物理存储抽象，业务层不能感知 NAS 或对象存储实现。
 */
public interface AttachmentStorage {

    void store(String objectKey, byte[] content);

    Resource load(String objectKey);

    boolean exists(String objectKey);

    void delete(String objectKey);
}
