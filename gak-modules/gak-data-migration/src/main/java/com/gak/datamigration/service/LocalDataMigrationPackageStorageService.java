package com.gak.datamigration.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import static com.gak.datamigration.DataMigrationConstants.FILE_STORAGE_LOCAL;

/**
 * 本地迁移包存储。
 */
@Service
public class LocalDataMigrationPackageStorageService implements DataMigrationPackageStorageService {

    private final Path packageDir;
    private final Path importDir;
    private final Path workspaceDir;

    public LocalDataMigrationPackageStorageService(
            @Value("${gak.data-migration.storage-dir:./data/data-migrations}") String storageDir) {
        Path root = Paths.get(storageDir).toAbsolutePath().normalize();
        this.packageDir = root.resolve("packages");
        this.importDir = root.resolve("imports");
        this.workspaceDir = root.resolve("workspace");
    }

    @Override
    public StoredPackageFile saveExportPackage(String fileName, Path tempFile) throws IOException {
        Files.createDirectories(packageDir);
        Path target = packageDir.resolve(normalizeFileName(fileName));
        Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        return new StoredPackageFile(FILE_STORAGE_LOCAL, target.toString(), target.getFileName().toString(), Files.size(target), target);
    }

    @Override
    public StoredPackageFile saveImportPackage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("导入文件不能为空");
        }
        Files.createDirectories(importDir);
        String originalFileName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename().trim() : "import.zip";
        String normalizedFileName = normalizeFileName(originalFileName);
        String savedName = System.currentTimeMillis() + "-" + normalizedFileName;
        Path target = importDir.resolve(savedName);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new StoredPackageFile(FILE_STORAGE_LOCAL, target.toString(), normalizedFileName, Files.size(target), target);
    }

    @Override
    public Path createTempDirectory(String prefix) throws IOException {
        Files.createDirectories(workspaceDir);
        return Files.createTempDirectory(workspaceDir, prefix);
    }

    @Override
    public Path createTempFile(String prefix, String suffix) throws IOException {
        Files.createDirectories(workspaceDir);
        return Files.createTempFile(workspaceDir, prefix, suffix);
    }

    @Override
    public Resource loadAsResource(String storagePath) throws IOException {
        Path path = resolve(storagePath);
        try {
            return new UrlResource(path.toUri());
        } catch (MalformedURLException exception) {
            throw new IOException("迁移包不存在", exception);
        }
    }

    @Override
    public Path resolve(String storagePath) {
        return Paths.get(storagePath).toAbsolutePath().normalize();
    }

    @Override
    public void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted((left, right) -> right.compareTo(left))
                    .forEach(current -> {
                        try {
                            Files.deleteIfExists(current);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private String normalizeFileName(String fileName) {
        String normalized = StringUtils.hasText(fileName) ? fileName.trim() : "package.zip";
        normalized = normalized.replace("\\", "_").replace("/", "_");
        if (normalized.contains("..")) {
            normalized = normalized.replace("..", "_");
        }
        return normalized;
    }
}
