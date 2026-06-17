package com.gak.datamigration.service;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataMigrationBeanMergeSupportTest {

    @Test
    void mergeNewestNonNullShouldSkipOlderSource() {
        DemoRecord source = new DemoRecord("nas-old", LocalDateTime.of(2026, 6, 1, 10, 0));
        DemoRecord existing = new DemoRecord("local-new", LocalDateTime.of(2026, 6, 2, 10, 0));

        boolean merged = DataMigrationBeanMergeSupport.mergeNewestNonNull(source, existing);

        assertFalse(merged);
        assertEquals("local-new", existing.getName());
    }

    @Test
    void mergeNewestNonNullShouldApplyNewerSource() {
        DemoRecord source = new DemoRecord("local-new", LocalDateTime.of(2026, 6, 2, 10, 0));
        DemoRecord existing = new DemoRecord("nas-old", LocalDateTime.of(2026, 6, 1, 10, 0));

        boolean merged = DataMigrationBeanMergeSupport.mergeNewestNonNull(source, existing);

        assertTrue(merged);
        assertEquals("local-new", existing.getName());
        assertEquals(LocalDateTime.of(2026, 6, 2, 10, 0), existing.getUpdatedAt());
    }

    static class DemoRecord {

        private String name;
        private LocalDateTime updatedAt;

        DemoRecord(String name, LocalDateTime updatedAt) {
            this.name = name;
            this.updatedAt = updatedAt;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}
