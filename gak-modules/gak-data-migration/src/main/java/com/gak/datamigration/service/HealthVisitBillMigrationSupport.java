package com.gak.datamigration.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.framework.exception.BusinessException;
import com.gak.healthrecord.domain.HealthVisit;
import com.gak.healthrecord.mapper.HealthVisitMapper;
import com.gak.healthrecord.service.HealthVisitBillSyncService;
import com.gak.personalbills.domain.PersonalBill;
import com.gak.personalbills.mapper.PersonalBillMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 健康来源账单随健康资源恢复，按目标就诊 ID 关联，不重复导入个人账单资源。 */
@Service
public class HealthVisitBillMigrationSupport {
    private static final String SOURCE_ENTRY = "PAYMENT";
    private final PersonalBillMapper bills;
    private final HealthVisitMapper visits;
    private final HealthVisitBillSyncService syncService;

    public HealthVisitBillMigrationSupport(PersonalBillMapper bills, HealthVisitMapper visits,
                                          HealthVisitBillSyncService syncService) {
        this.bills = bills;
        this.visits = visits;
        this.syncService = syncService;
    }

    public List<PersonalBill> exportBills() {
        return bills.selectList(new QueryWrapper<PersonalBill>()
                .eq("source_type", HealthVisitBillSyncService.SOURCE_TYPE)
                .orderByAsc("owner_user_id").orderByAsc("source_id").orderByAsc("id"));
    }

    /** 只恢复备份中实际关联的账单，或维护目标已有账单，不根据历史金额批量补账。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restore(List<PersonalBill> sourceBills, Map<Long, Long> visitIdMappings,
                        Function<Long, Long> resolveOwner) {
        for (PersonalBill source : sourceBills) {
            if (source == null || !HealthVisitBillSyncService.SOURCE_TYPE.equals(source.getSourceType())
                    || !SOURCE_ENTRY.equals(source.getSourceEntry()) || !visitIdMappings.containsKey(source.getSourceId())) {
                throw new BusinessException("DATA_MIGRATION_HEALTH_BILL_INVALID", "健康账单缺少对应就诊来源");
            }
            HealthVisit target = visits.selectById(visitIdMappings.get(source.getSourceId()));
            if (target == null || !target.getOwnerUserId().equals(resolveOwner.apply(source.getOwnerUserId()))) {
                throw new BusinessException("DATA_MIGRATION_HEALTH_BILL_OWNER_CONFLICT", "健康账单与就诊所属用户不一致");
            }
        }
        for (Map.Entry<Long, Long> mapping : visitIdMappings.entrySet()) {
            HealthVisit visit = visits.selectById(mapping.getValue());
            if (visit == null) {
                throw new BusinessException("DATA_MIGRATION_HEALTH_VISIT_MISSING", "健康账单对应的就诊记录不存在");
            }
            boolean sourceLinked = sourceBills.stream().anyMatch(bill -> mapping.getKey().equals(bill.getSourceId()));
            boolean targetLinked = bills.selectCount(new QueryWrapper<PersonalBill>()
                    .eq("owner_user_id", visit.getOwnerUserId()).eq("source_type", HealthVisitBillSyncService.SOURCE_TYPE)
                    .eq("source_id", visit.getId()).eq("source_entry", SOURCE_ENTRY)) > 0;
            if (sourceLinked || targetLinked) {
                // 实际合并后的就诊是金额、日期、医院的事实来源，避免旧备份覆盖新数据。
                syncService.sync(visit);
            }
        }
    }
}
