package com.gak.datamigration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.framework.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import static com.gak.datamigration.DataMigrationConstants.ATTACHMENT_INDEX_FILE;
import static com.gak.datamigration.DataMigrationConstants.COMPATIBLE_VERSION;
import static com.gak.datamigration.DataMigrationConstants.MANIFEST_FILE_NAME;
import static com.gak.datamigration.DataMigrationConstants.PACKAGE_VERSION;

/**
 * 迁移包 ZIP 与 manifest 处理。
 */
@Service
public class DataMigrationArchiveService {

    private static final int COPY_BUFFER_BYTES = 16 * 1024;
    private static final int MAX_ENTRIES = 10_000;
    private static final int MAX_PATH_DEPTH = 32;
    private static final long MAX_ENTRY_BYTES = 64L * 1024 * 1024;
    private static final long MAX_ARCHIVE_BYTES = 1024L * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final DataMigrationPackageStorageService storageService;

    public DataMigrationArchiveService(ObjectMapper objectMapper,
                                       DataMigrationPackageStorageService storageService) {
        this.objectMapper = objectMapper;
        this.storageService = storageService;
    }

    public BuildPackageResult buildExportPackage(BuildPackageRequest request,
                                                 List<com.gak.datamigration.handler.MigrationResourceHandler.MigrationResourceExportData> exports)
            throws IOException {
        Path workspace = storageService.createTempDirectory("export-");
        Path zipFile = null;
        boolean complete = false;
        try {
            Path root = MigrationPaths.resolveEntry(workspace, request.packageName());
            if (!workspace.equals(root.getParent())) {
                throw new BusinessException("DATA_MIGRATION_PATH_INVALID", "迁移包名称非法");
            }
            Files.createDirectories(root);
            List<AttachmentIndexItem> attachmentIndex = new ArrayList<>();
            for (com.gak.datamigration.handler.MigrationResourceHandler.MigrationResourceExportData export : exports) {
                Path entryPath = MigrationPaths.resolveEntry(root, export.entryPath());
                Files.createDirectories(entryPath.getParent());
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(entryPath.toFile(), export.payload());
                for (com.gak.datamigration.handler.MigrationResourceHandler.MigrationAttachment attachment : export.attachments()) {
                    Path attachmentPath = MigrationPaths.resolveEntry(root, attachment.entryPath());
                    Files.createDirectories(attachmentPath.getParent());
                    Files.copy(attachment.sourcePath(), attachmentPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    attachmentIndex.add(new AttachmentIndexItem(export.resourceCode(), attachment.entryPath(), attachment.fileName()));
                }
            }
            if (!attachmentIndex.isEmpty()) {
                Path indexPath = root.resolve(ATTACHMENT_INDEX_FILE).normalize();
                Files.createDirectories(indexPath.getParent());
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(indexPath.toFile(), attachmentIndex);
            }

            String checksum = calculateChecksum(root);
            MigrationPackageManifest manifest = new MigrationPackageManifest(
                    request.packageName(),
                    PACKAGE_VERSION,
                    request.sourceEnv(),
                    request.createdAt(),
                    request.createdBy(),
                    request.scopeMode(),
                    request.systemResourceCodes(),
                    request.businessAppCodes(),
                    request.recordCount(),
                    request.attachmentCount(),
                    checksum,
                    COMPATIBLE_VERSION,
                    resolveResourceVersions(exports)
            );
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(root.resolve(MANIFEST_FILE_NAME).toFile(), manifest);
            zipFile = storageService.createTempFile("export-package-", ".zip");
            zipDirectory(root, zipFile);
            complete = true;
            return new BuildPackageResult(zipFile, manifest);
        } finally {
            storageService.deleteQuietly(workspace);
            if (!complete && zipFile != null) {
                storageService.deleteQuietly(zipFile);
            }
        }
    }

    public ValidatedImportPackage validateImportPackage(Path zipFile) throws IOException {
        ExtractedPackage extractedPackage = extractPackage(zipFile);
        try {
            return new ValidatedImportPackage(extractedPackage.manifest());
        } finally {
            storageService.deleteQuietly(extractedPackage.workspace());
        }
    }

    public ExtractedPackage extractPackage(Path zipFile) throws IOException {
        Path workspace = storageService.createTempDirectory("import-");
        boolean complete = false;
        try {
            unzip(zipFile, workspace);
            Path manifestPath = findManifestPath(workspace);
            if (manifestPath == null) {
                throw new BusinessException("DATA_MIGRATION_MANIFEST_MISSING", "迁移包缺少 manifest.json");
            }
            MigrationPackageManifest manifest = objectMapper.readValue(manifestPath.toFile(), MigrationPackageManifest.class);
            validateManifest(manifest);
            String actualChecksum = calculateChecksum(manifestPath.getParent());
            if (!actualChecksum.equalsIgnoreCase(manifest.checksum())) {
                throw new BusinessException("DATA_MIGRATION_CHECKSUM_INVALID", "迁移包校验失败");
            }
            complete = true;
            return new ExtractedPackage(manifestPath.getParent(), workspace, manifest);
        } finally {
            if (!complete) {
                storageService.deleteQuietly(workspace);
            }
        }
    }

    public <T> T readJson(Path packageRoot, String entryPath, Class<T> type) throws IOException {
        return objectMapper.readValue(MigrationPaths.resolveEntry(packageRoot, entryPath).toFile(), type);
    }

    private void validateManifest(MigrationPackageManifest manifest) {
        if (manifest == null) {
            throw new BusinessException("DATA_MIGRATION_MANIFEST_INVALID", "迁移包 manifest 不合法");
        }
        if (!StringUtils.hasText(manifest.packageName()) || !StringUtils.hasText(manifest.createdBy())) {
            throw new BusinessException("DATA_MIGRATION_MANIFEST_INVALID", "迁移包 manifest 不合法");
        }
    }

    private Map<String, String> resolveResourceVersions(
            List<com.gak.datamigration.handler.MigrationResourceHandler.MigrationResourceExportData> exports) {
        Map<String, String> versions = new LinkedHashMap<>();
        for (com.gak.datamigration.handler.MigrationResourceHandler.MigrationResourceExportData export : exports) {
            versions.put(export.resourceCode(), PACKAGE_VERSION);
        }
        return versions;
    }

    private Path findManifestPath(Path workspace) throws IOException {
        try (var stream = Files.walk(workspace)) {
            List<Path> manifests = stream.filter(Files::isRegularFile)
                    .filter(path -> MANIFEST_FILE_NAME.equals(path.getFileName().toString())).limit(2).toList();
            if (manifests.size() > 1) {
                throw new BusinessException("DATA_MIGRATION_MANIFEST_INVALID", "迁移包包含多个 manifest.json");
            }
            return manifests.isEmpty() ? null : manifests.getFirst();
        }
    }

    private void unzip(Path zipFile, Path targetDir) throws IOException {
        try (InputStream inputStream = Files.newInputStream(zipFile);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            byte[] buffer = new byte[COPY_BUFFER_BYTES];
            int entries = 0;
            long total = 0;
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path target = MigrationPaths.resolveEntry(targetDir, entry.getName());
                if (++entries > MAX_ENTRIES || entry.getSize() > MAX_ENTRY_BYTES
                        || targetDir.relativize(target).getNameCount() > MAX_PATH_DEPTH) {
                    throw archiveLimitExceeded();
                }
                if (entry.isDirectory()) {
                    if (zipInputStream.read() != -1) {
                        throw archiveLimitExceeded();
                    }
                    Files.createDirectories(target);
                    continue;
                }
                long entryBytes = 0;
                Files.createDirectories(target.getParent());
                try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    int read;
                    while ((read = zipInputStream.read(buffer)) != -1) {
                        entryBytes += read;
                        total += read;
                        if (entryBytes > MAX_ENTRY_BYTES || total > MAX_ARCHIVE_BYTES) {
                            throw archiveLimitExceeded();
                        }
                        output.write(buffer, 0, read);
                    }
                }
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException("DATA_MIGRATION_ZIP_INVALID", "迁移包不是合法的 ZIP 文件");
        }
    }

    private BusinessException archiveLimitExceeded() {
        return new BusinessException("DATA_MIGRATION_ZIP_LIMIT", "迁移包解压大小、目录深度或条目数量超限");
    }

    private void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
        try (OutputStream outputStream = Files.newOutputStream(zipFile);
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            try (var stream = Files.walk(sourceDir)) {
                stream.filter(Files::isRegularFile)
                        .sorted(Comparator.naturalOrder())
                        .forEach(path -> {
                            ZipEntry entry = new ZipEntry(sourceDir.relativize(path).toString().replace('\\', '/'));
                            try {
                                zipOutputStream.putNextEntry(entry);
                                Files.copy(path, zipOutputStream);
                                zipOutputStream.closeEntry();
                            } catch (IOException exception) {
                                throw new IllegalStateException(exception);
                            }
                        });
            }
        } catch (IllegalStateException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    private String calculateChecksum(Path root) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var stream = Files.walk(root)) {
                for (Path path : stream.filter(Files::isRegularFile)
                        .sorted(Comparator.naturalOrder())
                        .filter(file -> !MANIFEST_FILE_NAME.equals(file.getFileName().toString())).toList()) {
                    updateDigest(digest, root, path);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 算法不可用", exception);
        }
    }

    private void updateDigest(MessageDigest digest, Path root, Path path) throws IOException {
        digest.update(rootRelative(root, path).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try (InputStream input = new java.security.DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
    }

    private String rootRelative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    /**
     * 导出请求描述。
     */
    public record BuildPackageRequest(String packageName,
                                      String sourceEnv,
                                      LocalDateTime createdAt,
                                      String createdBy,
                                      String scopeMode,
                                      List<String> systemResourceCodes,
                                      List<String> businessAppCodes,
                                      long recordCount,
                                      long attachmentCount) {
    }

    /**
     * 导出结果。
     */
    public record BuildPackageResult(Path zipFile, MigrationPackageManifest manifest) {
    }

    /**
     * 已校验导入包。
     */
    public record ValidatedImportPackage(MigrationPackageManifest manifest) {
    }

    /**
     * 已解压导入包。
     */
    public record ExtractedPackage(Path root, Path workspace, MigrationPackageManifest manifest) {
    }

    /**
     * 附件索引项。
     */
    public record AttachmentIndexItem(String resourceCode, String entryPath, String fileName) {
    }

    /**
     * 包 manifest。
     */
    public record MigrationPackageManifest(String packageName,
                                           String packageVersion,
                                           String sourceEnv,
                                           LocalDateTime createdAt,
                                           String createdBy,
                                           String scopeMode,
                                           List<String> systemResourceCodes,
                                           List<String> businessAppCodes,
                                           long recordCount,
                                           long attachmentCount,
                                           String checksum,
                                           String compatibleVersion,
                                           Map<String, String> resourceVersions) {
    }
}
