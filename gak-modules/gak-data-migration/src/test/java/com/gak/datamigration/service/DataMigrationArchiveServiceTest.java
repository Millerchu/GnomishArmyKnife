package com.gak.datamigration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.datamigration.handler.MigrationResourceHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataMigrationArchiveServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void buildAndValidateShouldRoundTripManifest() throws Exception {
        DataMigrationArchiveService archiveService = new DataMigrationArchiveService(
                new ObjectMapper().findAndRegisterModules(),
                new InMemoryStorageService(tempDir)
        );

        DataMigrationArchiveService.BuildPackageResult buildResult = archiveService.buildExportPackage(
                new DataMigrationArchiveService.BuildPackageRequest(
                        "demo-package",
                        "test-env",
                        LocalDateTime.of(2026, 3, 29, 10, 0, 0),
                        "admin",
                        "CUSTOM",
                        List.of("SYSTEM_USERS"),
                        List.of("APP_TODO_LIST"),
                        3L,
                        0L
                ),
                List.of(new MigrationResourceHandler.MigrationResourceExportData(
                        "SYSTEM_USERS",
                        "system/users.json",
                        Map.of("users", List.of(Map.of("id", 1, "username", "admin"))),
                        1L,
                        0L,
                        List.of()
                ))
        );

        assertTrue(Files.exists(buildResult.zipFile()));
        DataMigrationArchiveService.ValidatedImportPackage validated = archiveService.validateImportPackage(buildResult.zipFile());
        assertEquals("demo-package", validated.manifest().packageName());
        assertEquals("admin", validated.manifest().createdBy());
        assertNotNull(validated.manifest().checksum());
    }

    private static class InMemoryStorageService implements DataMigrationPackageStorageService {

        private final Path root;

        private InMemoryStorageService(Path root) {
            this.root = root;
        }

        @Override
        public StoredPackageFile saveExportPackage(String fileName, Path tempFile) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredPackageFile saveImportPackage(org.springframework.web.multipart.MultipartFile file) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path createTempDirectory(String prefix) throws IOException {
            return Files.createTempDirectory(root, prefix);
        }

        @Override
        public Path createTempFile(String prefix, String suffix) throws IOException {
            return Files.createTempFile(root, prefix, suffix);
        }

        @Override
        public org.springframework.core.io.Resource loadAsResource(String storagePath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path resolve(String storagePath) {
            return Path.of(storagePath);
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
    }
}
