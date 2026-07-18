package com.gak.datamigration;

import com.gak.datamigration.handler.FuelStatsMigrationHandler;
import com.gak.datamigration.handler.HealthRecordMigrationHandler;
import com.gak.datamigration.handler.KnowledgeBaseMigrationHandler;
import com.gak.datamigration.handler.PersonalBillsMigrationHandler;
import com.gak.datamigration.handler.SystemAuditLogsMigrationHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataMigrationResourceCoverageTest {

    @Test
    void missingBusinessApplicationsShouldHaveMigrationHandlers() {
        assertEquals(DataMigrationConstants.APP_PERSONAL_BILLS, new PersonalBillsMigrationHandler(null, null, null, null).resourceCode());
        assertEquals(DataMigrationConstants.APP_HEALTH_RECORD, new HealthRecordMigrationHandler(null, null, null, null, null, null, null, null).resourceCode());
        assertEquals(DataMigrationConstants.APP_KNOWLEDGE_BASE, new KnowledgeBaseMigrationHandler(null, null, null, null).resourceCode());
        assertEquals(DataMigrationConstants.APP_FUEL_STATS, new FuelStatsMigrationHandler(null, null, null, null, null).resourceCode());
    }

    @Test
    void auditLogsShouldBeExportableAsSystemResource() {
        assertEquals(DataMigrationConstants.SYSTEM_RESOURCE_AUDIT_LOGS, new SystemAuditLogsMigrationHandler(null, null, null, null).resourceCode());
    }
}
