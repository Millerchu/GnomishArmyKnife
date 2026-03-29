package com.gak.datamigration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.framework.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
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
        Path root = workspace.resolve(request.packageName());
        Files.createDirectories(root);
        List<AttachmentIndexItem> attachmentIndex = new ArrayList<>();

        try {
            for (com.gak.datamigration.handler.MigrationResourceHandler.MigrationResourceExportData export : exports) {
                Path entryPath = root.resolve(export.entryPath()).normalize();
                Files.createDirectories(entryPath.getParent());
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(entryPath.toFile(), export.payload());
                for (com.gak.datamigration.handler.MigrationResourceHandler.MigrationAttachment attachment : export.attachments()) {
                    Path attachmentPath = root.resolve(attachment.entryPath()).normalize();
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
                    COMPATIBLE_VERSION
            );
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(root.resolve(MANIFEST_FILE_NAME).toFile(), manifest);
            Path zipFile = storageService.createTempFile(request.packageName() + "-", ".zip");
            zipDirectory(root, zipFile);
            storageService.deleteQuietly(workspace);
            return new BuildPackageResult(zipFile, manifest);
        } catch (IOException exception) {
            storageService.deleteQuietly(workspace);
            throw exception;
        }
    }

    public ValidatedImportPackage validateImportPackage(Path zipFile) throws IOException {
        ExtractedPackage extractedPackage = extractPackage(zipFile);
        try {
            return new ValidatedImportPackage(extractedPackage.manifest());
        } finally {
            storageService.deleteQuietly(extractedPackage.root());
        }
    }

    public ExtractedPackage extractPackage(Path zipFile) throws IOException {
        Path workspace = storageService.createTempDirectory("import-");
        unzip(zipFile, workspace);
        Path manifestPath = findManifestPath(workspace);
        if (manifestPath == null) {
            storageService.deleteQuietly(workspace);
            throw new BusinessException("DATA_MIGRATION_MANIFEST_MISSING", "迁移包缺少 manifest.json");
        }
        MigrationPackageManifest manifest = objectMapper.readValue(manifestPath.toFile(), MigrationPackageManifest.class);
        validateManifest(manifest);
        String actualChecksum = calculateChecksum(manifestPath.getParent());
        if (!actualChecksum.equalsIgnoreCase(manifest.checksum())) {
            storageService.deleteQuietly(workspace);
            throw new BusinessException("DATA_MIGRATION_CHECKSUM_INVALID", "迁移包校验失败");
        }
        return new ExtractedPackage(manifestPath.getParent(), manifest);
    }

    public <T> T readJson(Path packageRoot, String entryPath, Class<T> type) throws IOException {
        return objectMapper.readValue(packageRoot.resolve(entryPath).toFile(), type);
    }

    private void validateManifest(MigrationPackageManifest manifest) {
        if (manifest == null) {
            throw new BusinessException("DATA_MIGRATION_MANIFEST_INVALID", "迁移包 manifest 不合法");
        }
        if (!StringUtils.hasText(manifest.packageName()) || !StringUtils.hasText(manifest.createdBy())) {
            throw new BusinessException("DATA_MIGRATION_MANIFEST_INVALID", "迁移包 manifest 不合法");
        }
    }

    private Path findManifestPath(Path workspace) throws IOException {
        try (var stream = Files.walk(workspace)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> MANIFEST_FILE_NAME.equals(path.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
        }
    }

    private void unzip(Path zipFile, Path targetDir) throws IOException {
        try (InputStream inputStream = Files.newInputStream(zipFile);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path target = targetDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(targetDir)) {
                    throw new BusinessException("DATA_MIGRATION_ZIP_INVALID", "迁移包目录结构非法");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (OutputStream outputStream = Files.newOutputStream(target)) {
                    zipInputStream.transferTo(outputStream);
                }
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException("DATA_MIGRATION_ZIP_INVALID", "迁移包不是合法的 ZIP 文件");
        }
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
                stream.filter(Files::isRegularFile)
                        .sorted(Comparator.naturalOrder())
                        .filter(path -> !MANIFEST_FILE_NAME.equals(path.getFileName().toString()))
                        .forEach(path -> updateDigest(digest, root, path));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 算法不可用", exception);
        }
    }

    private void updateDigest(MessageDigest digest, Path root, Path path) {
        try {
            digest.update(rootRelative(root, path).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update(Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
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
    public record ExtractedPackage(Path root, MigrationPackageManifest manifest) {
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
                                           String compatibleVersion) {
    }
}
