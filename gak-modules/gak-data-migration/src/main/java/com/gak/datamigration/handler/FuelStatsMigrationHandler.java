package com.gak.datamigration.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.service.DataMigrationArchiveService;
import com.gak.datamigration.service.DataMigrationBeanMergeSupport;
import com.gak.datamigration.service.DataMigrationQuerySupport;
import com.gak.framework.exception.BusinessException;
import com.gak.fuelstats.domain.FuelPriceSnapshot;
import com.gak.fuelstats.domain.FuelRecord;
import com.gak.fuelstats.mapper.FuelPriceSnapshotMapper;
import com.gak.fuelstats.mapper.FuelRecordMapper;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 油耗统计迁移处理器。
 */
@Service
public class FuelStatsMigrationHandler implements MigrationResourceHandler {

    private final FuelRecordMapper fuelRecordMapper;
    private final FuelPriceSnapshotMapper fuelPriceSnapshotMapper;
    private final UserMapper userMapper;
    private final DataMigrationArchiveService archiveService;

    public FuelStatsMigrationHandler(FuelRecordMapper fuelRecordMapper,
                                     FuelPriceSnapshotMapper fuelPriceSnapshotMapper,
                                     UserMapper userMapper,
                                     DataMigrationArchiveService archiveService) {
        this.fuelRecordMapper = fuelRecordMapper;
        this.fuelPriceSnapshotMapper = fuelPriceSnapshotMapper;
        this.userMapper = userMapper;
        this.archiveService = archiveService;
    }

    @Override
    public String resourceCode() {
        return DataMigrationConstants.APP_FUEL_STATS;
    }

    @Override
    public String resourceName() {
        return "油耗统计";
    }

    @Override
    public String resourceType() {
        return DataMigrationConstants.RESOURCE_TYPE_BUSINESS;
    }

    @Override
    public boolean attachmentSupported() {
        return false;
    }

    @Override
    public String entryPath() {
        return "business/" + resourceCode() + "/data.json";
    }

    @Override
    public int order() {
        return 160;
    }

    @Override
    public MigrationResourceExportData exportData(ExportContext context) {
        QueryWrapper<FuelRecord> recordWrapper = new QueryWrapper<>();
        recordWrapper.orderByAsc("owner_user_id").orderByAsc("fuel_date").orderByAsc("id");
        List<FuelRecord> records = fuelRecordMapper.selectList(recordWrapper);

        QueryWrapper<FuelPriceSnapshot> snapshotWrapper = new QueryWrapper<>();
        snapshotWrapper.orderByAsc("publish_date").orderByAsc("id");
        List<FuelPriceSnapshot> snapshots = fuelPriceSnapshotMapper.selectList(snapshotWrapper);

        long recordCount = (long) records.size() + snapshots.size();
        return new MigrationResourceExportData(resourceCode(), entryPath(), new Payload(records, snapshots), recordCount, 0L, List.of());
    }

    @Override
    @Transactional
    public MigrationResourceImportResult importData(ImportContext context) throws Exception {
        Payload payload = archiveService.readJson(context.packageRoot(), entryPath(), Payload.class);
        long importedCount = importRecords(context, DataMigrationQuerySupport.emptyIfNull(payload.getRecords()));
        importedCount += importSnapshots(context, DataMigrationQuerySupport.emptyIfNull(payload.getSnapshots()));
        return MigrationResourceImportResult.success(importedCount, 0L, "油耗统计导入完成");
    }

    private long importRecords(ImportContext context, List<FuelRecord> records) {
        long importedCount = 0L;
        for (FuelRecord source : records) {
            if (source == null) {
                continue;
            }
            Long targetUserId = resolveUserId(source.getOwnerUserId(), context);
            FuelRecord existing = findExistingRecord(source, targetUserId);
            source.setOwnerUserId(targetUserId);
            if (existing != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_FUEL_RECORD_CONFLICT", "加油记录已存在: " + source.getId());
                }
                if (context.isOverwrite()) {
                    DataMigrationBeanMergeSupport.overwriteNewest(source, existing, "id", "ownerUserId");
                } else {
                    DataMigrationBeanMergeSupport.mergeNewestNonNull(source, existing, "id", "ownerUserId");
                }
                existing.setOwnerUserId(targetUserId);
                fuelRecordMapper.updateById(existing);
            } else {
                FuelRecord insertRecord = copyRecord(source);
                insertRecord.setOwnerUserId(targetUserId);
                fuelRecordMapper.insert(insertRecord);
            }
            importedCount++;
        }
        return importedCount;
    }

    private long importSnapshots(ImportContext context, List<FuelPriceSnapshot> snapshots) {
        long importedCount = 0L;
        for (FuelPriceSnapshot source : snapshots) {
            if (source == null) {
                continue;
            }
            FuelPriceSnapshot existing = findExistingSnapshot(source);
            if (existing != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_FUEL_PRICE_CONFLICT", "油价快照已存在: " + source.getId());
                }
                if (context.isOverwrite()) {
                    DataMigrationBeanMergeSupport.overwriteNewest(source, existing, "id");
                } else {
                    DataMigrationBeanMergeSupport.mergeNewestNonNull(source, existing, "id");
                }
                fuelPriceSnapshotMapper.updateById(existing);
            } else {
                FuelPriceSnapshot insertSnapshot = copySnapshot(source);
                fuelPriceSnapshotMapper.insert(insertSnapshot);
            }
            importedCount++;
        }
        return importedCount;
    }

    private FuelRecord findExistingRecord(FuelRecord source, Long targetUserId) {
        FuelRecord byId = source.getId() == null ? null : fuelRecordMapper.selectById(source.getId());
        if (byId != null) {
            return byId;
        }
        QueryWrapper<FuelRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", targetUserId)
                .eq("vehicle_name", source.getVehicleName())
                .eq("fuel_date", source.getFuelDate())
                .eq("odometer_km", source.getOdometerKm());
        return fuelRecordMapper.selectOne(wrapper);
    }

    private FuelPriceSnapshot findExistingSnapshot(FuelPriceSnapshot source) {
        FuelPriceSnapshot byId = source.getId() == null ? null : fuelPriceSnapshotMapper.selectById(source.getId());
        if (byId != null) {
            return byId;
        }
        QueryWrapper<FuelPriceSnapshot> wrapper = new QueryWrapper<>();
        wrapper.eq("publish_date", source.getPublishDate());
        return fuelPriceSnapshotMapper.selectOne(wrapper);
    }

    private Long resolveUserId(Long sourceUserId, ImportContext context) {
        Long mappedId = context.mappedUserId(sourceUserId);
        if (mappedId != null) {
            return mappedId;
        }
        User sameUser = sourceUserId == null ? null : userMapper.selectById(sourceUserId);
        if (sameUser == null) {
            throw new BusinessException("DATA_MIGRATION_FUEL_USER_MISSING", "油耗记录依赖的用户不存在: " + sourceUserId);
        }
        return sameUser.getId();
    }

    private FuelRecord copyRecord(FuelRecord source) {
        FuelRecord record = new FuelRecord();
        DataMigrationBeanMergeSupport.overwrite(source, record);
        return record;
    }

    private FuelPriceSnapshot copySnapshot(FuelPriceSnapshot source) {
        FuelPriceSnapshot snapshot = new FuelPriceSnapshot();
        DataMigrationBeanMergeSupport.overwrite(source, snapshot);
        return snapshot;
    }

    /**
     * 油耗统计导出载荷。
     */
    public static class Payload {

        private List<FuelRecord> records;
        private List<FuelPriceSnapshot> snapshots;

        public Payload() {
        }

        public Payload(List<FuelRecord> records, List<FuelPriceSnapshot> snapshots) {
            this.records = records;
            this.snapshots = snapshots;
        }

        public List<FuelRecord> getRecords() {
            return records;
        }

        public void setRecords(List<FuelRecord> records) {
            this.records = records;
        }

        public List<FuelPriceSnapshot> getSnapshots() {
            return snapshots;
        }

        public void setSnapshots(List<FuelPriceSnapshot> snapshots) {
            this.snapshots = snapshots;
        }
    }
}
