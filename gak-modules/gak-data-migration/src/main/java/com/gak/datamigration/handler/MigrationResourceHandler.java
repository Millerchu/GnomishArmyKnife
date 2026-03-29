package com.gak.datamigration.handler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资源迁移处理器。
 */
public interface MigrationResourceHandler {

    String resourceCode();

    String resourceName();

    String resourceType();

    boolean attachmentSupported();

    String entryPath();

    int order();

    MigrationResourceExportData exportData(ExportContext context) throws Exception;

    MigrationResourceImportResult importData(ImportContext context) throws Exception;

    /**
     * 导出上下文。
     */
    final class ExportContext {

        private final boolean includeAttachments;

        public ExportContext(boolean includeAttachments) {
            this.includeAttachments = includeAttachments;
        }

        public boolean includeAttachments() {
            return includeAttachments;
        }
    }

    /**
     * 导入上下文。
     */
    final class ImportContext {

        private final String importMode;
        private final boolean includeAttachments;
        private final Path packageRoot;
        private final Map<Long, Long> userIdMappings = new LinkedHashMap<>();
        private final Map<Long, Long> appIdMappings = new LinkedHashMap<>();
        private final Map<Long, Long> dictionaryIdMappings = new LinkedHashMap<>();

        public ImportContext(String importMode, boolean includeAttachments, Path packageRoot) {
            this.importMode = importMode;
            this.includeAttachments = includeAttachments;
            this.packageRoot = packageRoot;
        }

        public String importMode() {
            return importMode;
        }

        public boolean includeAttachments() {
            return includeAttachments;
        }

        public Path packageRoot() {
            return packageRoot;
        }

        public boolean isStrict() {
            return "STRICT".equalsIgnoreCase(importMode);
        }

        public boolean isOverwrite() {
            return "OVERWRITE".equalsIgnoreCase(importMode);
        }

        public boolean isMerge() {
            return "MERGE".equalsIgnoreCase(importMode);
        }

        public void mapUserId(Long sourceUserId, Long targetUserId) {
            if (sourceUserId != null && targetUserId != null) {
                userIdMappings.put(sourceUserId, targetUserId);
            }
        }

        public void mapAppId(Long sourceAppId, Long targetAppId) {
            if (sourceAppId != null && targetAppId != null) {
                appIdMappings.put(sourceAppId, targetAppId);
            }
        }

        public void mapDictionaryId(Long sourceDictionaryId, Long targetDictionaryId) {
            if (sourceDictionaryId != null && targetDictionaryId != null) {
                dictionaryIdMappings.put(sourceDictionaryId, targetDictionaryId);
            }
        }

        public Long mappedUserId(Long sourceUserId) {
            return sourceUserId == null ? null : userIdMappings.get(sourceUserId);
        }

        public Long mappedAppId(Long sourceAppId) {
            return sourceAppId == null ? null : appIdMappings.get(sourceAppId);
        }

        public Long mappedDictionaryId(Long sourceDictionaryId) {
            return sourceDictionaryId == null ? null : dictionaryIdMappings.get(sourceDictionaryId);
        }

        public Path attachmentPath(String relativePath) {
            return packageRoot.resolve(relativePath).normalize();
        }
    }

    /**
     * 导出结果。
     */
    record MigrationResourceExportData(String resourceCode,
                                       String entryPath,
                                       Object payload,
                                       long recordCount,
                                       long attachmentCount,
                                       List<MigrationAttachment> attachments) {

        public MigrationResourceExportData {
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    /**
     * 附件描述。
     */
    record MigrationAttachment(String entryPath, String fileName, Path sourcePath) {
    }

    /**
     * 导入结果。
     */
    record MigrationResourceImportResult(long recordCount, long attachmentCount, String message) {

        public static MigrationResourceImportResult success(long recordCount, long attachmentCount, String message) {
            return new MigrationResourceImportResult(recordCount, attachmentCount, message);
        }

        public static MigrationResourceImportResult empty() {
            return new MigrationResourceImportResult(0L, 0L, null);
        }
    }

    /**
     * 空附件集合。
     */
    static List<MigrationAttachment> noAttachments() {
        return new ArrayList<>();
    }
}
