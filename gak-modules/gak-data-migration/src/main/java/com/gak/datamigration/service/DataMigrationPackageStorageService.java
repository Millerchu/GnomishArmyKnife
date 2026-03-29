package com.gak.datamigration.service;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 迁移包存储服务。
 */
public interface DataMigrationPackageStorageService {

    StoredPackageFile saveExportPackage(String fileName, Path tempFile) throws IOException;

    StoredPackageFile saveImportPackage(MultipartFile file) throws IOException;

    Path createTempDirectory(String prefix) throws IOException;

    Path createTempFile(String prefix, String suffix) throws IOException;

    Resource loadAsResource(String storagePath) throws IOException;

    Path resolve(String storagePath);

    void deleteQuietly(Path path);

    /**
     * 已保存文件。
     */
    record StoredPackageFile(String storageType, String storagePath, String fileName, long fileSize, Path localPath) {
    }
}
