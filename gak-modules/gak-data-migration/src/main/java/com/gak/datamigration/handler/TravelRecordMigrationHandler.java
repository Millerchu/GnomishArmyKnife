package com.gak.datamigration.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.attachment.constant.AttachmentConstants;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.dto.TravelRecordMigrationPayload;
import com.gak.datamigration.service.AttachmentMigrationSupport;
import com.gak.datamigration.service.AttachmentMigrationSupport.ExportBundle;
import com.gak.datamigration.service.DataMigrationArchiveService;
import com.gak.datamigration.service.DataMigrationBeanMergeSupport;
import com.gak.datamigration.service.DataMigrationQuerySupport;
import com.gak.framework.exception.BusinessException;
import com.gak.personalbills.domain.PersonalBill;
import com.gak.personalbills.mapper.PersonalBillMapper;
import com.gak.travelrecord.domain.TravelGroup;
import com.gak.travelrecord.domain.TravelRecord;
import com.gak.travelrecord.mapper.TravelGroupMapper;
import com.gak.travelrecord.mapper.TravelRecordMapper;
import com.gak.user.mapper.user.UserMapper;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 将行程与其费用整体恢复，不调用会再次生成账单的业务保存接口。 */
@Service
public class TravelRecordMigrationHandler implements MigrationResourceHandler {
    private static final String BILL_SOURCE = "TRAVEL_RECORD";
    private static final int MAX_ATTACHMENTS = 9;
    private final TravelRecordMapper recordMapper;
    private final TravelGroupMapper groupMapper;
    private final PersonalBillMapper billMapper;
    private final UserMapper userMapper;
    private final DataMigrationArchiveService archiveService;
    private final AttachmentMigrationSupport attachmentSupport;

    public TravelRecordMigrationHandler(TravelRecordMapper recordMapper, TravelGroupMapper groupMapper,
            PersonalBillMapper billMapper, UserMapper userMapper, DataMigrationArchiveService archiveService,
            AttachmentMigrationSupport attachmentSupport) {
        this.recordMapper = recordMapper;
        this.groupMapper = groupMapper;
        this.billMapper = billMapper;
        this.userMapper = userMapper;
        this.archiveService = archiveService;
        this.attachmentSupport = attachmentSupport;
    }

    @Override public String resourceCode() { return DataMigrationConstants.APP_TRAVEL_RECORD; }
    @Override public String resourceName() { return "个人行程记录（含关联账单）"; }
    @Override public String resourceType() { return DataMigrationConstants.RESOURCE_TYPE_BUSINESS; }
    @Override public boolean attachmentSupported() { return true; }
    @Override public String entryPath() { return "business/" + resourceCode() + "/data.json"; }
    @Override public int order() { return 170; }

    @Override
    public MigrationResourceExportData exportData(ExportContext context) {
        List<TravelRecord> records = recordMapper.selectList(new QueryWrapper<TravelRecord>()
                .orderByAsc("owner_user_id", "departure_date", "id"));
        List<TravelGroup> groups = groupMapper.selectList(new QueryWrapper<TravelGroup>()
                .orderByAsc("owner_user_id", "id"));
        Set<Long> detachedBillIds = new HashSet<>();
        for (TravelRecord record : records) {
            if (record.getDetachedPaymentBillId() != null) detachedBillIds.add(record.getDetachedPaymentBillId());
            if (record.getDetachedRefundBillId() != null) detachedBillIds.add(record.getDetachedRefundBillId());
        }
        QueryWrapper<PersonalBill> billQuery = new QueryWrapper<>();
        billQuery.and(query -> {
            query.eq("source_type", BILL_SOURCE);
            if (!detachedBillIds.isEmpty()) query.or().in("id", detachedBillIds);
        }).orderByAsc("owner_user_id", "id");
        List<PersonalBill> bills = billMapper.selectList(billQuery);
        // 解除同步后的账单允许用户独立删除；备份不能携带已不存在的引用。
        Set<Long> exportedBillIds = new HashSet<>(bills.stream().map(PersonalBill::getId).toList());
        for (TravelRecord record : records) {
            if (record.getDetachedPaymentBillId() != null && !exportedBillIds.contains(record.getDetachedPaymentBillId())) {
                record.setDetachedPaymentBillId(null);
            }
            if (record.getDetachedRefundBillId() != null && !exportedBillIds.contains(record.getDetachedRefundBillId())) {
                record.setDetachedRefundBillId(null);
            }
        }
        ExportBundle bundle = context.includeAttachments()
                ? attachmentSupport.collect(AttachmentConstants.BUSINESS_TRAVEL_RECORD,
                records.stream().map(TravelRecord::getId).toList(), "attachments/travel-records")
                : new ExportBundle(List.of(), List.of());
        TravelRecordMigrationPayload payload = new TravelRecordMigrationPayload();
        payload.setRecords(records);
        payload.setGroups(groups);
        payload.setBills(bills);
        payload.setAttachments(bundle.items());
        return new MigrationResourceExportData(resourceCode(), entryPath(), payload,
                (long) records.size() + groups.size() + bills.size(), bundle.files().size(), bundle.files());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MigrationResourceImportResult importData(ImportContext context) throws Exception {
        TravelRecordMigrationPayload payload = archiveService.readJson(context.packageRoot(), entryPath(),
                TravelRecordMigrationPayload.class);
        List<TravelRecord> records = DataMigrationQuerySupport.emptyIfNull(payload.getRecords());
        List<TravelGroup> groups = DataMigrationQuerySupport.emptyIfNull(payload.getGroups());
        List<PersonalBill> bills = DataMigrationQuerySupport.emptyIfNull(payload.getBills());
        validateRelations(records, groups, bills);
        validateAttachmentFiles(payload, records, context);
        Map<Long, Long> recordIds = new LinkedHashMap<>();
        for (TravelGroup source : groups) {
            TravelGroup target = new TravelGroup();
            DataMigrationBeanMergeSupport.overwrite(source, target);
            target.setOwnerUserId(resolveOwner(source.getOwnerUserId(), context));
            save(target, groupMapper, TravelGroup::getId, TravelGroup::getOwnerUserId, context);
        }
        for (TravelRecord source : records) {
            TravelRecord target = new TravelRecord();
            DataMigrationBeanMergeSupport.overwrite(source, target);
            target.setOwnerUserId(resolveOwner(source.getOwnerUserId(), context));
            save(target, recordMapper, TravelRecord::getId, TravelRecord::getOwnerUserId, context);
            recordIds.put(source.getId(), target.getId());
        }
        for (PersonalBill source : bills) {
            PersonalBill target = new PersonalBill();
            DataMigrationBeanMergeSupport.overwrite(source, target);
            target.setOwnerUserId(resolveOwner(source.getOwnerUserId(), context));
            validateExistingBill(target);
            save(target, billMapper, PersonalBill::getId, PersonalBill::getOwnerUserId, context);
        }
        long attachments = attachmentSupport.restore(context, AttachmentConstants.BUSINESS_TRAVEL_RECORD,
                DataMigrationQuerySupport.emptyIfNull(payload.getAttachments()), recordIds,
                id -> {
                    TravelRecord record = recordMapper.selectById(id);
                    return record == null ? null : record.getOwnerUserId();
                }, MAX_ATTACHMENTS);
        return MigrationResourceImportResult.success((long) records.size() + groups.size() + bills.size(),
                attachments, "个人行程及关联账单导入完成");
    }

    private void validateAttachmentFiles(TravelRecordMigrationPayload payload, List<TravelRecord> records,
            ImportContext context) {
        if (!context.includeAttachments()) return;
        Set<Long> recordIds = new HashSet<>(records.stream().map(TravelRecord::getId).toList());
        Path packageRoot = context.packageRoot().toAbsolutePath().normalize();
        for (AttachmentMigrationSupport.TransferItem attachment
                : DataMigrationQuerySupport.emptyIfNull(payload.getAttachments())) {
            require(attachment != null && attachment.getEntryPath() != null
                    && recordIds.contains(attachment.getSourceBusinessId()), "票据附件关联无效");
            Path file = packageRoot.resolve(attachment.getEntryPath()).normalize();
            require(file.startsWith(packageRoot) && Files.isRegularFile(file),
                    "票据原件缺失或路径无效: " + attachment.getEntryPath());
        }
    }

    private void validateRelations(List<TravelRecord> records, List<TravelGroup> groups, List<PersonalBill> bills) {
        Map<Long, TravelGroup> groupsById = index(groups, TravelGroup::getId);
        Map<Long, TravelRecord> recordsById = index(records, TravelRecord::getId);
        Map<Long, PersonalBill> billsById = index(bills, PersonalBill::getId);
        Map<Long, Long> detachedOwners = new LinkedHashMap<>();
        for (TravelRecord record : records) {
            validateDetached(record.getDetachedPaymentBillId(), record, billsById, detachedOwners);
            validateDetached(record.getDetachedRefundBillId(), record, billsById, detachedOwners);
            if (record.getGroupId() != null) {
                TravelGroup group = groupsById.get(record.getGroupId());
                require(group != null && Objects.equals(group.getOwnerUserId(), record.getOwnerUserId()),
                        "行程分组不存在或归属不一致");
            }
            Set<Long> ancestors = new HashSet<>();
            TravelRecord current = record;
            while (current != null) {
                require(ancestors.add(current.getId()), "改签记录存在循环关联");
                Long previousId = current.getPreviousRecordId();
                current = previousId == null ? null : recordsById.get(previousId);
                require(previousId == null || current != null
                        && Objects.equals(current.getOwnerUserId(), record.getOwnerUserId()),
                        "原行程不存在或归属不一致");
            }
        }
        Set<String> sourceEntries = new HashSet<>();
        for (PersonalBill bill : bills) {
            if (bill.getSourceType() == null && detachedOwners.containsKey(bill.getId())) {
                continue;
            }
            TravelRecord record = recordsById.get(bill.getSourceId());
            require(BILL_SOURCE.equals(bill.getSourceType()) && record != null
                    && Objects.equals(record.getOwnerUserId(), bill.getOwnerUserId()), "行程账单关联无效");
            require("PAYMENT".equals(bill.getSourceEntry()) || "REFUND".equals(bill.getSourceEntry()),
                    "行程账单类型无效");
            require(sourceEntries.add(bill.getSourceId() + ":" + bill.getSourceEntry()), "行程账单重复");
        }
    }

    private void validateDetached(Long billId, TravelRecord record, Map<Long, PersonalBill> bills,
            Map<Long, Long> detachedOwners) {
        if (billId == null) return;
        PersonalBill bill = bills.get(billId);
        require(bill != null && bill.getSourceType() == null
                && Objects.equals(bill.getOwnerUserId(), record.getOwnerUserId()), "保留的行程账单缺失或归属不一致");
        require(detachedOwners.putIfAbsent(billId, record.getOwnerUserId()) == null, "保留账单被多条行程重复引用");
    }

    private void validateExistingBill(PersonalBill target) {
        PersonalBill existing = billMapper.selectById(target.getId());
        require(existing == null || Objects.equals(existing.getSourceType(), target.getSourceType())
                && Objects.equals(existing.getSourceId(), target.getSourceId())
                && Objects.equals(existing.getSourceEntry(), target.getSourceEntry()), "账单 ID 与其他业务冲突");
    }

    private <T> Map<Long, T> index(List<T> records, Function<T, Long> idGetter) {
        Map<Long, T> result = new LinkedHashMap<>();
        for (T record : records) {
            require(record != null && idGetter.apply(record) != null, "迁移记录缺少 ID");
            require(result.putIfAbsent(idGetter.apply(record), record) == null, "迁移记录 ID 重复");
        }
        return result;
    }

    /** 保留稳定 ID 保证重复恢复幂等；跨用户碰撞直接拒绝，绝不覆盖他人记录。 */
    private <T> void save(T source, BaseMapper<T> mapper, Function<T, Long> idGetter,
            Function<T, Long> ownerGetter, ImportContext context) {
        T existing = mapper.selectById(idGetter.apply(source));
        if (existing == null) {
            mapper.insert(source);
            return;
        }
        require(Objects.equals(ownerGetter.apply(source), ownerGetter.apply(existing)), "迁移 ID 与其他用户记录冲突");
        require(!context.isStrict(), "行程迁移记录已存在: " + idGetter.apply(source));
        // 金额、退款、币种和来源是一个快照，逐字段合并会拼出从未存在过的账务状态。
        if (context.isOverwrite() || existing instanceof TravelRecord || existing instanceof PersonalBill) {
            DataMigrationBeanMergeSupport.overwriteNewest(source, existing, "id", "ownerUserId", "version");
        } else {
            DataMigrationBeanMergeSupport.mergeNewestNonNull(source, existing, "id", "ownerUserId", "version");
        }
        if (existing instanceof TravelRecord record) {
            record.setVersion(record.getVersion() == null ? 1 : record.getVersion() + 1);
        }
        require(mapper.updateById(existing) == 1, "迁移期间记录发生变化，请重试");
    }

    private Long resolveOwner(Long sourceId, ImportContext context) {
        Long mapped = context.mappedUserId(sourceId);
        Long targetId = mapped == null ? sourceId : mapped;
        require(targetId != null && userMapper.selectById(targetId) != null, "行程依赖的用户不存在: " + sourceId);
        return targetId;
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException("DATA_MIGRATION_TRAVEL_INVALID", message);
        }
    }
}
