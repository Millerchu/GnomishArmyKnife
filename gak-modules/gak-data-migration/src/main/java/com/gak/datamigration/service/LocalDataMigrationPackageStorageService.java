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
        Path target = MigrationPaths.resolveEntry(packageDir, normalizeFileName(fileName));
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
        Path target = Files.createTempFile(importDir, "import-", ".zip");
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            deleteQuietly(target);
            throw exception;
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
        Path path = Paths.get(storagePath).toAbsolutePath().normalize();
        Path base = path.startsWith(packageDir) ? packageDir : importDir;
        if (path.equals(base) || !path.startsWith(base) || escapesRealPath(base, path)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "迁移包不存在");
        }
        return path;
    }

    @Override
    public void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        Path target = path.toAbsolutePath().normalize();
        Path base = target.startsWith(workspaceDir) ? workspaceDir
                : target.startsWith(packageDir) ? packageDir : importDir;
        if (target.equals(base) || !target.startsWith(base) || !Files.exists(target) || escapesRealPath(base, target)) {
            return;
        }
        try (var stream = Files.walk(target)) {
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

    private boolean escapesRealPath(Path base, Path path) {
        try {
            return Files.exists(path) && !path.toRealPath().startsWith(base.toRealPath());
        } catch (IOException exception) {
            return true;
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
