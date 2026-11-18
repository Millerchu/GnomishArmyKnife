package com.gak.aisubscription.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.aisubscription.domain.AiBillLink;
import com.gak.aisubscription.domain.AiPayment;
import com.gak.aisubscription.mapper.AiIntegrationMapper;
import com.gak.aisubscription.vo.AiBillLinkVO;
import com.gak.personalbills.domain.PersonalBill;
import com.gak.personalbills.mapper.PersonalBillMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 手工账单只引用；生成账单以支付来源唯一键保护重试，解绑不删除历史。 */
@Service
public class AiBillLinkService {
    public static final String SOURCE = "AI_SUBSCRIPTION";
    private static final String EXPENSE = "EXPENSE";
    private static final String ENTRY = "PAYMENT";
    private static final BigDecimal MAX_BILL_AMOUNT = new BigDecimal("9999999999.99");
    private final AiAccessService access;
    private final AiPaymentService payments;
    private final AiIntegrationMapper links;
    private final PersonalBillMapper bills;

    public AiBillLinkService(AiAccessService access, AiPaymentService payments,
            AiIntegrationMapper links, PersonalBillMapper bills) {
        this.access = access;
        this.payments = payments;
        this.links = links;
        this.bills = bills;
    }

    public AiBillLinkVO get(Long owner, Long paymentId) {
        authorize(owner, paymentId);
        AiBillLink link = links.link(owner, paymentId);
        if (link == null) { return null; }
        PersonalBill bill = bills.selectById(link.billId());
        if (bill == null || !Objects.equals(owner, bill.getOwnerUserId())) {
            return new AiBillLinkVO(null, null, null, null, link.generated(), false);
        }
        return view(bill, link.generated());
    }

    public List<AiBillLinkVO> candidates(Long owner, Long paymentId) {
        AiPayment payment = authorize(owner, paymentId);
        return bills.selectList(new QueryWrapper<PersonalBill>().eq("owner_user_id", owner)
                .eq("bill_type", EXPENSE).eq("currency", payment.getCurrency())
                .eq("amount", signedAmount(payment)).eq("bill_date", payment.getPaymentDate())
                .isNull("source_type").orderByDesc("id").last("LIMIT 100")).stream()
                .filter(bill -> links.linkedPayment(bill.getId()) == null)
                .map(bill -> view(bill, false)).toList();
    }

    @Transactional
    public AiBillLinkVO link(Long owner, Long paymentId, Long existingBillId) {
        access.lockOwner(owner);
        AiPayment payment = authorize(owner, paymentId);
        AiBillLink current = links.link(owner, paymentId);
        if (current != null) {
            if (existingBillId != null && !existingBillId.equals(current.billId())) {
                throw invalid("已有关联账单，请先解绑");
            }
            return get(owner, paymentId);
        }
        // 解绑后再次生成，复用来源账单，不能产生第二笔现金支出。
        PersonalBill generated = bills.selectOne(new QueryWrapper<PersonalBill>().eq("owner_user_id", owner)
                .eq("source_type", SOURCE).eq("source_id", paymentId).eq("source_entry", ENTRY));
        if (generated != null && existingBillId != null && !existingBillId.equals(generated.getId())) {
            throw invalid("此支付已有生成账单，请重新关联原账单，避免重复记账");
        }
        boolean isGenerated = generated != null || existingBillId == null;
        PersonalBill bill = generated != null ? generated
                : existingBillId == null ? createBill(owner, payment)
                : bills.selectOwnedForUpdate(existingBillId, owner);
        validateBill(payment, bill, isGenerated);
        if (links.linkedPayment(bill.getId()) != null) { throw invalid("该账单已关联其他支付"); }
        links.insertLink(new AiBillLink(owner, paymentId, bill.getId(), isGenerated));
        return view(bill, isGenerated);
    }

    @Transactional
    public void unlink(Long owner, Long paymentId) {
        access.lockOwner(owner);
        authorize(owner, paymentId);
        links.unlink(owner, paymentId);
    }

    private AiPayment authorize(Long owner, Long paymentId) {
        access.requireApp(owner, "APP_AI_SUBSCRIPTION");
        access.requireApp(owner, "APP_PERSONAL_BILLS");
        return payments.requireOwned(owner, paymentId);
    }

    private PersonalBill createBill(Long owner, AiPayment payment) {
        if (payment.getAmount().stripTrailingZeros().scale() > 2) {
            throw invalid("个人账单仅支持两位小数，当前支付金额无法无损生成账单");
        }
        if (payment.getAmount().compareTo(MAX_BILL_AMOUNT) > 0) {
            throw invalid("支付金额超过个人账单可保存范围");
        }
        PersonalBill bill = new PersonalBill();
        bill.setOwnerUserId(owner);
        bill.setBillType(EXPENSE);
        bill.setCategoryName("AI 服务");
        bill.setAmount(signedAmount(payment));
        bill.setCurrency(payment.getCurrency());
        bill.setBillDate(payment.getPaymentDate());
        bill.setSourceType(SOURCE);
        bill.setSourceId(payment.getId());
        bill.setSourceEntry(ENTRY);
        bill.setNote("智订 · " + payment.getType());
        bill.setCreatedAt(LocalDateTime.now());
        bill.setUpdatedAt(LocalDateTime.now());
        bills.insert(bill);
        return bill;
    }

    private void validateBill(AiPayment payment, PersonalBill bill, boolean generated) {
        if (bill == null || !Objects.equals(payment.getOwnerUserId(), bill.getOwnerUserId())
                || !EXPENSE.equals(bill.getBillType()) || (!generated && bill.getSourceType() != null)
                || !Objects.equals(payment.getCurrency(), bill.getCurrency())
                || signedAmount(payment).compareTo(bill.getAmount()) != 0
                || !Objects.equals(payment.getPaymentDate(), bill.getBillDate())) {
            throw invalid("请选择本人金额、币种和支付日期一致的未关联支出账单");
        }
    }

    private BigDecimal signedAmount(AiPayment payment) {
        return "REFUND".equals(payment.getType()) ? payment.getAmount().negate() : payment.getAmount();
    }

    private AiBillLinkVO view(PersonalBill bill, boolean generated) {
        return new AiBillLinkVO(bill.getId().toString(), bill.getCurrency(), bill.getAmount(),
                bill.getBillDate(), generated, true);
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
