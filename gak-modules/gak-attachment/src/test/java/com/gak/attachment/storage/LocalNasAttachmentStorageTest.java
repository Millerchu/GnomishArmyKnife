package com.gak.attachment.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gak.framework.exception.BusinessException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalNasAttachmentStorageTest {

    @TempDir
    Path storageRoot;

    @Test
    void shouldStoreLoadAndDeleteWithinConfiguredRoot() throws Exception {
        LocalNasAttachmentStorage storage = new LocalNasAttachmentStorage(storageRoot.toString());
        byte[] content = "attachment-content".getBytes();

        storage.store("attachments/1/2026/07/file.txt", content);

        assertTrue(storage.exists("attachments/1/2026/07/file.txt"));
        assertArrayEquals(content, storage.load("attachments/1/2026/07/file.txt").getInputStream().readAllBytes());
        storage.delete("attachments/1/2026/07/file.txt");
        assertFalse(storage.exists("attachments/1/2026/07/file.txt"));
    }

    @Test
    void shouldRejectPathTraversal() {
        LocalNasAttachmentStorage storage = new LocalNasAttachmentStorage(storageRoot.toString());

        assertThrows(BusinessException.class, () -> storage.store("../outside.txt", new byte[]{1}));
    }
}
