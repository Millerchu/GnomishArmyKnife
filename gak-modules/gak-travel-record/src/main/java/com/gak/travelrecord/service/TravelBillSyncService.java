package com.gak.travelrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.gak.framework.exception.BusinessException;
import com.gak.personalbills.domain.PersonalBill;
import com.gak.personalbills.mapper.PersonalBillMapper;
import com.gak.personalbills.vo.PersonalBillVO;
import com.gak.travelrecord.domain.TravelRecord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 行程账单只在行程事务内写入，以唯一来源条目实现幂等同步。 */
@Service
public class TravelBillSyncService {
    public static final String SOURCE_TYPE = "TRAVEL_RECORD";
    private static final String PAYMENT = "PAYMENT";
    private static final String REFUND = "REFUND";
    private final PersonalBillMapper mapper;

    public TravelBillSyncService(PersonalBillMapper mapper) { this.mapper = mapper; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void sync(TravelRecord record, Long existingBillId) {
        List<PersonalBill> bills = linked(record.getOwnerUserId(), record.getId());
        if (!Boolean.TRUE.equals(record.getSyncBill())) {
            if (!bills.isEmpty()) {
                record.setBillingDetached(true);
                PersonalBill payment = entry(bills, PAYMENT);
                PersonalBill refund = entry(bills, REFUND);
                record.setDetachedPaymentBillId(payment == null ? null : payment.getId());
                record.setDetachedRefundBillId(refund == null ? null : refund.getId());
                detachOrDelete(record.getOwnerUserId(), record.getId(), false);
            }
            return;
        }
        if (Boolean.TRUE.equals(record.getBillingDetached()) && existingBillId == null
                && record.getDetachedPaymentBillId() != null
                && mapper.selectOwnedForUpdate(record.getDetachedPaymentBillId(), record.getOwnerUserId()) != null) {
            throw new BusinessException("TRAVEL_RELINK_REQUIRED", "原账单已保留，请选择关联已有账单后重新开启同步");
        }
        if (Boolean.TRUE.equals(record.getBillingDetached()) && record.getDetachedPaymentBillId() != null
                && !record.getDetachedPaymentBillId().equals(existingBillId)
                && mapper.selectOwnedForUpdate(record.getDetachedPaymentBillId(), record.getOwnerUserId()) != null) {
            throw new BusinessException("TRAVEL_RELINK_REQUIRED", "请重新关联原付款账单，避免原支出被重复记录");
        }
        if (record.getPaidAmount() == null && !bills.isEmpty()) {
            throw new BusinessException("TRAVEL_PAYMENT_IN_USE", "已有账单时不能清空金额，可先关闭同步保留账单");
        }
        PersonalBill payment = entry(bills, PAYMENT);
        if (existingBillId != null && (payment == null || !existingBillId.equals(payment.getId()))) {
            if (payment != null) {
                throw new BusinessException("TRAVEL_BILL_ALREADY_LINKED", "当前行程已有关联账单，请先解除原关联");
            }
            payment = claimExisting(record, existingBillId);
        }
        upsert(record, payment, PAYMENT, record.getPaidAmount(), paymentDate(record));
        BigDecimal refund = record.getRefundAmount() == null ? null : record.getRefundAmount().negate();
        PersonalBill refundBill = entry(bills, REFUND);
        if (refundBill == null && Boolean.TRUE.equals(record.getBillingDetached()) && record.getDetachedRefundBillId() != null) {
            refundBill = mapper.selectOwnedForUpdate(record.getDetachedRefundBillId(), record.getOwnerUserId());
            if (refundBill != null && (refundBill.getSourceType() != null || !"EXPENSE".equals(refundBill.getBillType())
                    || !Objects.equals(refundBill.getCurrency(), record.getCurrency())
                    || refund == null || refundBill.getAmount().compareTo(refund) != 0
                    || !Objects.equals(refundBill.getBillDate(), record.getRefundDate()))) {
                throw new BusinessException("TRAVEL_REFUND_RELINK_INVALID", "原退款账单已变更，请核对原退款金额、币种及日期后重新关联");
            }
        }
        upsert(record, refundBill, REFUND, refund, record.getRefundDate());
        record.setBillingDetached(false);
        record.setDetachedPaymentBillId(null);
        record.setDetachedRefundBillId(null);
    }

    private PersonalBill claimExisting(TravelRecord record, Long billId) {
        PersonalBill bill = mapper.selectOwnedForUpdate(billId, record.getOwnerUserId());
        if (bill == null || bill.getSourceType() != null || !"EXPENSE".equals(bill.getBillType())
                || !Objects.equals(bill.getCurrency(), record.getCurrency())
                || bill.getAmount().compareTo(record.getPaidAmount()) != 0
                || !Objects.equals(bill.getBillDate(), paymentDate(record))) {
            throw new BusinessException("TRAVEL_BILL_LINK_INVALID", "请选择本人未关联且金额、币种、支付日期一致的支出账单");
        }
        return bill;
    }

    private void upsert(TravelRecord record, PersonalBill bill, String entry, BigDecimal amount, LocalDate date) {
        if (amount == null || amount.signum() == 0) {
            if (bill != null) { mapper.deleteById(bill.getId()); }
            return;
        }
        boolean create = bill == null;
        if (create) {
            bill = new PersonalBill();
            bill.setOwnerUserId(record.getOwnerUserId());
            bill.setCreatedAt(LocalDateTime.now());
            bill.setCategoryName("交通");
        }
        bill.setBillType("EXPENSE");
        bill.setCurrency(record.getCurrency());
        bill.setAmount(amount.setScale(2, RoundingMode.UNNECESSARY));
        bill.setBillDate(date);
        bill.setSourceType(SOURCE_TYPE);
        bill.setSourceId(record.getId());
        bill.setSourceEntry(entry);
        bill.setMerchantName(record.getCarrier());
        bill.setNote((REFUND.equals(entry) ? "行程退款 · " : "行程付款 · ")
                + record.getDeparture() + " → " + record.getArrival()
                + " · " + transportLabel(record.getTransportType()));
        bill.setUpdatedAt(LocalDateTime.now());
        if (create) { mapper.insert(bill); } else { mapper.updateById(bill); }
    }

    private String transportLabel(String type) {
        return switch (type) {
            case "TRAIN" -> "火车";
            case "FLIGHT" -> "飞机";
            case "BUS" -> "汽车";
            case "SHIP" -> "轮船";
            default -> "其他";
        };
    }

    private PersonalBill entry(List<PersonalBill> bills, String entry) {
        return bills.stream().filter(bill -> entry.equals(bill.getSourceEntry())).findFirst().orElse(null);
    }

    private LocalDate paymentDate(TravelRecord record) {
        return record.getPaymentDate() != null ? record.getPaymentDate() : record.getDepartureDate();
    }

    private QueryWrapper<PersonalBill> sourceQuery(Long owner, Long recordId) {
        return new QueryWrapper<PersonalBill>().eq("owner_user_id", owner)
                .eq("source_type", SOURCE_TYPE).eq("source_id", recordId);
    }

    public List<PersonalBill> linked(Long owner, Long recordId) {
        return mapper.selectList(sourceQuery(owner, recordId).orderByAsc("source_entry").orderByAsc("id"));
    }

    public List<PersonalBillVO> views(Long owner, Long recordId) {
        return linked(owner, recordId).stream().map(this::toView).toList();
    }

    public List<PersonalBillVO> candidates(Long owner, String currency, String keyword, LocalDate paymentDate, BigDecimal amount) {
        QueryWrapper<PersonalBill> query = new QueryWrapper<PersonalBill>().eq("owner_user_id", owner)
                .isNull("source_type").eq("bill_type", "EXPENSE").gt("amount", BigDecimal.ZERO)
                .eq("currency", currency);
        if (paymentDate != null) { query.eq("bill_date", paymentDate); }
        if (amount != null) { query.eq("amount", amount); }
        if (keyword != null && !keyword.isBlank()) {
            query.and(part -> part.like("note", keyword.trim()).or().like("merchant_name", keyword.trim()));
        }
        return mapper.selectList(query.orderByDesc("bill_date").orderByDesc("id").last("LIMIT 100"))
                .stream().map(this::toView).toList();
    }

    private PersonalBillVO toView(PersonalBill bill) {
        PersonalBillVO view = new PersonalBillVO();
        BeanUtils.copyProperties(bill, view);
        return view;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void detachOrDelete(Long owner, Long recordId, boolean delete) {
        if (delete) {
            mapper.delete(sourceQuery(owner, recordId));
        } else {
            mapper.update(null, new UpdateWrapper<PersonalBill>().eq("owner_user_id", owner)
                    .eq("source_type", SOURCE_TYPE).eq("source_id", recordId)
                    .set("source_type", null).set("source_id", null).set("source_entry", null)
                    .set("updated_at", LocalDateTime.now()));
        }
    }
}
