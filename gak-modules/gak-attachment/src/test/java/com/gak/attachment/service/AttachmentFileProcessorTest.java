package com.gak.attachment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gak.framework.exception.BusinessException;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AttachmentFileProcessorTest {

    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    private final AttachmentFileProcessor processor = new AttachmentFileProcessor();

    @Test
    void shouldDetectImageAndGenerateThumbnail() {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "text/plain", ONE_PIXEL_PNG);

        AttachmentFileProcessor.ProcessedFile result = processor.process(file, "IMAGE");

        assertEquals("image/png", result.contentType());
        assertEquals(".png", result.extension());
        assertNotNull(result.thumbnail());
        assertEquals(64, result.sha256().length());
    }

    @Test
    void shouldRejectExtensionDisguisedAsImage() {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", "not-an-image".getBytes());

        assertThrows(BusinessException.class, () -> processor.process(file, "IMAGE"));
    }

    @Test
    void shouldRejectDocumentForImageUsage() {
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "%PDF-1.7\n".getBytes());

        assertThrows(BusinessException.class, () -> processor.process(file, "IMAGE"));
    }
}
