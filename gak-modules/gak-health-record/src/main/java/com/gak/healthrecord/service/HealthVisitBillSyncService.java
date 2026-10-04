package com.gak.healthrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gak.healthrecord.domain.HealthVisit;
import com.gak.personalbills.domain.PersonalBill;
import com.gak.personalbills.mapper.PersonalBillMapper;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 就诊实际自付支出随源记录在同一事务内维护。 */
@Service
public class HealthVisitBillSyncService {
    public static final String SOURCE_TYPE = "HEALTH_VISIT";
    private static final String SOURCE_ENTRY = "PAYMENT";
    private static final String BILL_TYPE = "EXPENSE";
    private static final String CATEGORY = "医疗";
    private static final String CURRENCY = "CNY";
    private static final String NOTE = "医院就诊";
    private static final int MONEY_SCALE = 2;
    private final PersonalBillMapper mapper;

    public HealthVisitBillSyncService(PersonalBillMapper mapper) {
        this.mapper = mapper;
    }

    /** 历史就诊补填金额也记账；清空或零金额撤销来源支出。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sync(HealthVisit visit) {
        if (visit.getAmount() == null || visit.getAmount().signum() == 0) {
            delete(visit.getOwnerUserId(), visit.getId());
            return;
        }
        PersonalBill bill = mapper.selectOne(sourceQuery(visit.getOwnerUserId(), visit.getId()));
        LocalDateTime now = LocalDateTime.now();
        if (bill == null) {
            bill = new PersonalBill();
            bill.setOwnerUserId(visit.getOwnerUserId());
            bill.setSourceType(SOURCE_TYPE);
            bill.setSourceId(visit.getId());
            bill.setSourceEntry(SOURCE_ENTRY);
            bill.setBillType(BILL_TYPE);
            bill.setCategoryName(CATEGORY);
            bill.setCurrency(CURRENCY);
            bill.setAmount(visit.getAmount().setScale(MONEY_SCALE, RoundingMode.UNNECESSARY));
            bill.setBillDate(visit.getVisitDate());
            bill.setMerchantName(visit.getHospitalName());
            // 账单仅记录支付信息，不扩散诊断和病历等健康隐私。
            bill.setNote(NOTE);
            bill.setCreatedAt(now);
            bill.setUpdatedAt(now);
            mapper.insert(bill);
            return;
        }
        mapper.update(null, new UpdateWrapper<PersonalBill>().eq("id", bill.getId())
                .eq("owner_user_id", visit.getOwnerUserId()).eq("source_type", SOURCE_TYPE)
                .eq("source_id", visit.getId()).eq("source_entry", SOURCE_ENTRY)
                .set("amount", visit.getAmount().setScale(MONEY_SCALE, RoundingMode.UNNECESSARY))
                .set("bill_date", visit.getVisitDate()).set("merchant_name", visit.getHospitalName())
                .set("note", NOTE).set("updated_at", now));
    }

    /** 不影响手工账单和其他用户或其他来源的账单。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(Long ownerUserId, Long visitId) {
        mapper.delete(sourceQuery(ownerUserId, visitId));
    }

    private QueryWrapper<PersonalBill> sourceQuery(Long ownerUserId, Long visitId) {
        return new QueryWrapper<PersonalBill>().eq("owner_user_id", ownerUserId)
                .eq("source_type", SOURCE_TYPE).eq("source_id", visitId).eq("source_entry", SOURCE_ENTRY);
    }
}
