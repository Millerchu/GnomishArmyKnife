package com.gak.attachment.storage;

import com.gak.framework.exception.BusinessException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 基于 NAS 挂载目录的附件存储实现。
 */
@Component
public class LocalNasAttachmentStorage implements AttachmentStorage {

    private final Path storageRoot;

    public LocalNasAttachmentStorage(@Value("${gak.attachment.storage-root:./data}") String storageRoot) {
        this.storageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    @Override
    public void store(String objectKey, byte[] content) {
        Path target = resolveSafely(objectKey);
        Path temporary = target.resolveSibling(target.getFileName() + ".uploading-" + UUID.randomUUID());
        try {
            Files.createDirectories(target.getParent());
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            tryDelete(temporary);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "附件保存失败");
        }
    }

    @Override
    public Resource load(String objectKey) {
        Path target = resolveSafely(objectKey);
        if (!Files.isRegularFile(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在");
        }
        try {
            return new UrlResource(target.toUri());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在");
        }
    }

    @Override
    public boolean exists(String objectKey) {
        return objectKey != null && Files.isRegularFile(resolveSafely(objectKey));
    }

    @Override
    public void delete(String objectKey) {
        if (objectKey == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolveSafely(objectKey));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "附件清理失败");
        }
    }

    private Path resolveSafely(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("/") || objectKey.contains("..")) {
            throw new BusinessException("ATTACHMENT_OBJECT_KEY_INVALID", "附件存储路径非法");
        }
        Path resolved = storageRoot.resolve(objectKey).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new BusinessException("ATTACHMENT_OBJECT_KEY_INVALID", "附件存储路径非法");
        }
        return resolved;
    }

    private void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 保存失败时的补偿删除不应覆盖原始异常。
        }
    }
}
