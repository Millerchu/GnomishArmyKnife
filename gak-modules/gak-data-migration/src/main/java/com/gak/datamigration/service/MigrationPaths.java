package com.gak.datamigration.service;

import com.gak.framework.exception.BusinessException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** 在不同操作系统上按同一规则解释包内相对路径。 */
public final class MigrationPaths {
    private MigrationPaths() {
    }

    public static Path resolveEntry(Path root, String entry) {
        try {
            if (entry == null || entry.isBlank() || entry.indexOf((char) 92) >= 0 || entry.indexOf(':') >= 0) {
                throw invalidPath();
            }
            Path relative = Path.of(entry);
            for (Path part : relative) {
                if ("..".equals(part.toString())) {
                    throw invalidPath();
                }
            }
            Path target = root.resolve(relative).normalize();
            if (relative.isAbsolute() || target.equals(root) || !target.startsWith(root)) {
                throw invalidPath();
            }
            return target;
        } catch (InvalidPathException exception) {
            throw invalidPath();
        }
    }

    private static BusinessException invalidPath() {
        return new BusinessException("DATA_MIGRATION_PATH_INVALID", "迁移包路径非法");
    }
}
