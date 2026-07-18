package com.gak.attachment.service;

import static com.gak.attachment.constant.AttachmentConstants.USAGE_ATTACHMENT;
import static com.gak.attachment.constant.AttachmentConstants.USAGE_ICON;
import static com.gak.attachment.constant.AttachmentConstants.USAGE_IMAGE;

import com.gak.framework.exception.BusinessException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件内容校验、类型识别和缩略图生成。
 */
@Component
public class AttachmentFileProcessor {

    private static final long IMAGE_MAX_SIZE = 10L * 1024L * 1024L;
    private static final long ICON_MAX_SIZE = 2L * 1024L * 1024L;
    private static final long ATTACHMENT_MAX_SIZE = 50L * 1024L * 1024L;
    private static final int THUMBNAIL_MAX_EDGE = 320;
    private static final Set<String> VALID_USAGE_TYPES = Set.of(USAGE_ATTACHMENT, USAGE_IMAGE, USAGE_ICON);
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            ".pdf", ".docx", ".xlsx", ".pptx", ".txt", ".csv", ".zip"
    );
    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.ofEntries(
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/png", ".png"),
            Map.entry("image/gif", ".gif"),
            Map.entry("image/webp", ".webp"),
            Map.entry("application/pdf", ".pdf"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", ".pptx"),
            Map.entry("text/plain", ".txt"),
            Map.entry("text/csv", ".csv"),
            Map.entry("application/zip", ".zip")
    );

    public ProcessedFile process(MultipartFile file, String usageType) {
        String normalizedUsageType = normalizeUsageType(usageType);
        validateBasic(file, normalizedUsageType);
        try {
            return process(file.getBytes(), file.getOriginalFilename(), normalizedUsageType);
        } catch (IOException exception) {
            throw new BusinessException("ATTACHMENT_READ_FAILED", "附件读取失败");
        }
    }

    public ProcessedFile process(byte[] content, String originalFileName, String usageType) {
        String normalizedUsageType = normalizeUsageType(usageType);
        if (content == null || content.length == 0) {
            throw new BusinessException("ATTACHMENT_FILE_REQUIRED", "上传文件不能为空");
        }
        long maxSize = USAGE_ICON.equals(normalizedUsageType) ? ICON_MAX_SIZE
                : USAGE_IMAGE.equals(normalizedUsageType) ? IMAGE_MAX_SIZE : ATTACHMENT_MAX_SIZE;
        if (content.length > maxSize) {
            throw new BusinessException("ATTACHMENT_FILE_TOO_LARGE", "上传文件超过允许大小");
        }
        String normalizedFileName = normalizeOriginalFileName(originalFileName);
        String extension = resolveOriginalExtension(normalizedFileName);
        String contentType = detectContentType(content, extension);
        validateAllowedType(normalizedUsageType, extension, contentType);
        byte[] thumbnail = IMAGE_CONTENT_TYPES.contains(contentType) ? createThumbnail(content, contentType) : null;
        return new ProcessedFile(normalizedFileName, extension, contentType, content, thumbnail, sha256(content));
    }

    private String normalizeUsageType(String usageType) {
        String normalized = StringUtils.hasText(usageType) ? usageType.trim().toUpperCase(Locale.ROOT) : null;
        if (normalized == null || !VALID_USAGE_TYPES.contains(normalized)) {
            throw new BusinessException("ATTACHMENT_USAGE_TYPE_INVALID", "附件用途类型不支持");
        }
        return normalized;
    }

    private void validateBasic(MultipartFile file, String usageType) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("ATTACHMENT_FILE_REQUIRED", "上传文件不能为空");
        }
        long maxSize = USAGE_ICON.equals(usageType) ? ICON_MAX_SIZE
                : USAGE_IMAGE.equals(usageType) ? IMAGE_MAX_SIZE : ATTACHMENT_MAX_SIZE;
        if (file.getSize() > maxSize) {
            throw new BusinessException("ATTACHMENT_FILE_TOO_LARGE", "上传文件超过允许大小");
        }
    }

    private String normalizeOriginalFileName(String fileName) {
        String normalized = StringUtils.hasText(fileName) ? fileName.trim() : "attachment";
        normalized = normalized.replace('\\', '_').replace('/', '_').replace('\0', '_');
        return normalized.length() > 255 ? normalized.substring(normalized.length() - 255) : normalized;
    }

    private String resolveOriginalExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            throw new BusinessException("ATTACHMENT_EXTENSION_REQUIRED", "上传文件缺少扩展名");
        }
        return fileName.substring(dotIndex).toLowerCase(Locale.ROOT);
    }

    private String detectContentType(byte[] content, String extension) {
        if (startsWith(content, new int[]{0xFF, 0xD8, 0xFF})) return "image/jpeg";
        if (startsWith(content, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) return "image/png";
        if (startsWithAscii(content, "GIF87a") || startsWithAscii(content, "GIF89a")) return "image/gif";
        if (content.length >= 12 && startsWithAscii(content, "RIFF") && asciiAt(content, 8, "WEBP")) return "image/webp";
        if (startsWithAscii(content, "%PDF-")) return "application/pdf";
        if (startsWith(content, new int[]{0x50, 0x4B, 0x03, 0x04})) return detectZipContentType(content, extension);
        if ((".txt".equals(extension) || ".csv".equals(extension)) && isPlainText(content)) {
            return ".csv".equals(extension) ? "text/csv" : "text/plain";
        }
        throw new BusinessException("ATTACHMENT_CONTENT_TYPE_INVALID", "文件真实类型不在允许范围内");
    }

    private String detectZipContentType(byte[] content, String extension) {
        if (".zip".equals(extension)) return "application/zip";
        String expectedEntry = switch (extension) {
            case ".docx" -> "word/";
            case ".xlsx" -> "xl/";
            case ".pptx" -> "ppt/";
            default -> null;
        };
        if (expectedEntry == null) {
            throw new BusinessException("ATTACHMENT_CONTENT_TYPE_INVALID", "压缩文件扩展名不匹配");
        }
        try (ZipInputStream inputStream = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = inputStream.getNextEntry()) != null) {
                if (entry.getName().startsWith(expectedEntry)) {
                    return extensionToContentType(extension);
                }
            }
        } catch (IOException exception) {
            throw new BusinessException("ATTACHMENT_CONTENT_INVALID", "Office 文件结构损坏");
        }
        throw new BusinessException("ATTACHMENT_CONTENT_INVALID", "Office 文件内容与扩展名不匹配");
    }

    private void validateAllowedType(String usageType, String extension, String contentType) {
        if (USAGE_IMAGE.equals(usageType) || USAGE_ICON.equals(usageType)) {
            if (!IMAGE_CONTENT_TYPES.contains(contentType)) {
                throw new BusinessException("ATTACHMENT_IMAGE_TYPE_INVALID", "仅支持 JPEG、PNG、WebP、GIF 图片");
            }
            return;
        }
        if (!IMAGE_CONTENT_TYPES.contains(contentType) && !DOCUMENT_EXTENSIONS.contains(extension)) {
            throw new BusinessException("ATTACHMENT_FILE_TYPE_INVALID", "附件文件类型不支持");
        }
        String expectedExtension = CONTENT_TYPE_EXTENSIONS.get(contentType);
        if (expectedExtension != null && !extensionMatches(extension, expectedExtension, contentType)) {
            throw new BusinessException("ATTACHMENT_EXTENSION_MISMATCH", "文件扩展名与真实内容不匹配");
        }
    }

    private boolean extensionMatches(String actual, String expected, String contentType) {
        return actual.equals(expected) || ("image/jpeg".equals(contentType) && ".jpeg".equals(actual));
    }

    private byte[] createThumbnail(byte[] content, String contentType) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
            if (source == null) {
                throw new BusinessException("ATTACHMENT_IMAGE_DAMAGED", "图片无法解码或已经损坏");
            }
            double scale = Math.min(1D, (double) THUMBNAIL_MAX_EDGE / Math.max(source.getWidth(), source.getHeight()));
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            boolean preserveAlpha = "image/png".equals(contentType);
            BufferedImage thumbnail = new BufferedImage(width, height,
                    preserveAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = thumbnail.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
            graphics.dispose();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(thumbnail, preserveAlpha ? "png" : "jpg", outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException("ATTACHMENT_THUMBNAIL_FAILED", "图片缩略图生成失败");
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    private boolean startsWith(byte[] content, int[] signature) {
        if (content.length < signature.length) return false;
        for (int index = 0; index < signature.length; index++) {
            if ((content[index] & 0xFF) != signature[index]) return false;
        }
        return true;
    }

    private boolean startsWithAscii(byte[] content, String signature) {
        return asciiAt(content, 0, signature);
    }

    private boolean asciiAt(byte[] content, int offset, String signature) {
        byte[] expected = signature.getBytes(StandardCharsets.US_ASCII);
        if (content.length < offset + expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if (content[offset + index] != expected[index]) return false;
        }
        return true;
    }

    private boolean isPlainText(byte[] content) {
        int sampleSize = Math.min(content.length, 8192);
        for (int index = 0; index < sampleSize; index++) {
            int value = content[index] & 0xFF;
            if (value == 0 || (value < 0x09) || (value > 0x0D && value < 0x20)) return false;
        }
        return true;
    }

    private String extensionToContentType(String extension) {
        return CONTENT_TYPE_EXTENSIONS.entrySet().stream()
                .filter(entry -> entry.getValue().equals(extension))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
    }

    public record ProcessedFile(String originalFileName, String extension, String contentType,
                                byte[] content, byte[] thumbnail, String sha256) {
    }
}
