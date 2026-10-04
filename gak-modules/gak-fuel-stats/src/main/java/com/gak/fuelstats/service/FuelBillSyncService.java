package com.gak.fuelstats.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gak.fuelstats.domain.FuelRecord;
import com.gak.personalbills.domain.PersonalBill;
import com.gak.personalbills.mapper.PersonalBillMapper;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 能源记录账单在源记录事务内维护，以来源唯一索引防止重复支出。 */
@Service
public class FuelBillSyncService {
    public static final String SOURCE_TYPE = "FUEL_RECORD";
    private static final String SOURCE_ENTRY = "PAYMENT";
    private static final String BILL_TYPE = "EXPENSE";
    private static final String CATEGORY = "交通";
    private static final String CURRENCY = "CNY";
    private static final String ELECTRIC_FUEL_TYPE = "ELECTRIC";
    private static final int MONEY_SCALE = 2;
    private final PersonalBillMapper mapper;

    public FuelBillSyncService(PersonalBillMapper mapper) {
        this.mapper = mapper;
    }

    /** 只有新建源记录允许生成账单；编辑未关联历史记录不自动补账。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sync(FuelRecord record, boolean allowCreate) {
        PersonalBill bill = mapper.selectOne(sourceQuery(record.getOwnerUserId(), record.getId()));
        if (bill == null && !allowCreate) {
            return;
        }
        boolean creating = bill == null;
        LocalDateTime now = LocalDateTime.now();
        if (creating) {
            bill = new PersonalBill();
            bill.setCreatedAt(now);
        }
        bill.setOwnerUserId(record.getOwnerUserId());
        bill.setSourceType(SOURCE_TYPE);
        bill.setSourceId(record.getId());
        bill.setSourceEntry(SOURCE_ENTRY);
        bill.setBillType(BILL_TYPE);
        bill.setCategoryName(CATEGORY);
        bill.setCurrency(CURRENCY);
        bill.setAmount(record.getDiscountedAmount().setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        bill.setBillDate(record.getFuelDate());
        bill.setMerchantName(record.getStationName());
        bill.setNote((ELECTRIC_FUEL_TYPE.equals(record.getFuelType()) ? "充电 · " : "加油 · ") + record.getVehicleName());
        bill.setUpdatedAt(now);
        if (creating) {
            mapper.insert(bill);
        } else {
            // 显式 SET 可选字段，清空油站时也清空商户，避免默认非空更新策略保留旧值。
            mapper.update(null, new UpdateWrapper<PersonalBill>().eq("id", bill.getId())
                    .eq("owner_user_id", record.getOwnerUserId()).eq("source_type", SOURCE_TYPE)
                    .eq("source_id", record.getId()).eq("source_entry", SOURCE_ENTRY)
                    .set("amount", bill.getAmount()).set("bill_date", bill.getBillDate())
                    .set("merchant_name", bill.getMerchantName()).set("note", bill.getNote())
                    .set("updated_at", now));
        }
    }

    /** 只删除同一用户和能源记录的来源支出，不影响手工账单。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(Long ownerUserId, Long recordId) {
        mapper.delete(sourceQuery(ownerUserId, recordId));
    }

    private QueryWrapper<PersonalBill> sourceQuery(Long ownerUserId, Long recordId) {
        return new QueryWrapper<PersonalBill>().eq("owner_user_id", ownerUserId)
                .eq("source_type", SOURCE_TYPE).eq("source_id", recordId).eq("source_entry", SOURCE_ENTRY);
    }
}
