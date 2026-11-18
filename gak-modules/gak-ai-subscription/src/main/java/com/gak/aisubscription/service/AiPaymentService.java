package com.gak.aisubscription.service;
import com.gak.aisubscription.domain.*;
import com.gak.aisubscription.dto.SaveAiPaymentRequest;
import com.gak.aisubscription.mapper.AiPaymentMapper;
import com.gak.aisubscription.vo.AiPaymentVO;
import com.gak.aisubscription.enums.AiPaymentType;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
/** 支付不可覆盖；用户行锁保护重试幂等和累计退款上限。 */
@Service
public class AiPaymentService {
 private final AiPaymentMapper mapper;
 private final AiAccountService accounts;
 private final AiSubscriptionService subscriptions;
 private final AiAccessService access;
 public AiPaymentService(AiPaymentMapper mapper,AiAccountService accounts,AiSubscriptionService subscriptions,AiAccessService access){this.mapper=mapper;this.accounts=accounts;this.subscriptions=subscriptions;this.access=access;}
 public List<AiPayment> records(Long owner){return mapper.selectList(new QueryWrapper<AiPayment>().eq("owner_user_id",owner).orderByDesc("payment_date").orderByDesc("id"));}
 public List<AiPaymentVO> list(Long owner){return records(owner).stream().map(this::toVO).toList();}
 public AiPayment requireOwned(Long owner,Long id){AiPayment payment=mapper.selectById(id);if(payment==null||!Objects.equals(owner,payment.getOwnerUserId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"支付记录不可用");return payment;}
 @Transactional public AiPaymentVO create(Long owner,SaveAiPaymentRequest r){
  access.lockOwner(owner);
  AiPayment previous=mapper.selectOne(new QueryWrapper<AiPayment>().eq("owner_user_id",owner).eq("request_key",r.requestKey()));
  if(previous!=null){AiRules.check(sameRequest(previous,r),"请求标识已用于不同支付，请重新提交");return toVO(previous);}
  // 归档后允许退款，禁止新增付款。
  if(r.type()==AiPaymentType.REFUND)accounts.requireOwned(owner,r.accountId());else accounts.requireActive(owner,r.accountId());
  AiPayment payment=new AiPayment();
  payment.setOwnerUserId(owner);payment.setAccountId(r.accountId());payment.setType(r.type().name());payment.setAmount(r.amount());payment.setCurrency(r.currency());payment.setPaymentDate(r.paymentDate());payment.setRequestKey(r.requestKey());payment.setNote(r.note());payment.setRecordedAt(LocalDateTime.now());
  if(r.type()==AiPaymentType.REFUND)applyRefund(owner,payment,r);
  else if(r.type()==AiPaymentType.SUBSCRIPTION)applySubscription(owner,payment,r);
  else {AiRules.check(r.originalPaymentId()==null&&r.subscriptionId()==null&&r.coverageStart()==null&&r.coverageEnd()==null,"充值与结算无需关联订阅覆盖周期");}
  mapper.insert(payment);return toVO(payment);
 }
 private void applySubscription(Long owner,AiPayment payment,SaveAiPaymentRequest r){
  AiRules.check(r.subscriptionId()!=null,"订阅支付必须选择订阅");AiRules.check(r.originalPaymentId()==null,"原支付仅用于退款");
  AiSubscription subscription=subscriptions.requireOwned(owner,r.subscriptionId());
  AiRules.check(subscription.getAccountId().equals(r.accountId()),"订阅与支付账号不一致");
  AiRules.check(subscription.getCurrency().equals(r.currency()),"订阅支付币种与套餐不一致");AiRules.interval(r.coverageStart(),r.coverageEnd());
  boolean overlapping=records(owner).stream().anyMatch(p->"SUBSCRIPTION".equals(p.getType())&&Objects.equals(p.getSubscriptionId(),r.subscriptionId())&&AiRules.overlap(p.getCoverageStart(),p.getCoverageEnd(),r.coverageStart(),r.coverageEnd()));
  AiRules.check(!overlapping,"该订阅已有重叠的确认支付周期，请核对记录");
  payment.setSubscriptionId(r.subscriptionId());payment.setCoverageStart(r.coverageStart());payment.setCoverageEnd(r.coverageEnd());
 }
 private void applyRefund(Long owner,AiPayment payment,SaveAiPaymentRequest r){
  AiRules.check(r.originalPaymentId()!=null,"退款必须关联原支付");AiPayment original=requireOwned(owner,r.originalPaymentId());
  AiRules.check(!"REFUND".equals(original.getType()),"不能对退款再次退款");AiRules.check(original.getAccountId().equals(r.accountId())&&original.getCurrency().equals(r.currency()),"退款账号和币种必须与原支付相同");
  AiRules.check(!r.paymentDate().isBefore(original.getPaymentDate()),"退款日期不能早于原支付");
  BigDecimal refunded=records(owner).stream().filter(p->Objects.equals(p.getOriginalPaymentId(),original.getId())).map(AiPayment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
  AiRules.check(refunded.add(r.amount()).compareTo(original.getAmount())<=0,"累计退款不能超过原支付金额");
  payment.setOriginalPaymentId(original.getId());payment.setSubscriptionId(original.getSubscriptionId());payment.setCoverageStart(original.getCoverageStart());payment.setCoverageEnd(original.getCoverageEnd());
 }
 private boolean sameRequest(AiPayment p,SaveAiPaymentRequest r){return Objects.equals(p.getAccountId(),r.accountId())&&p.getType().equals(r.type().name())&&p.getAmount().compareTo(r.amount())==0&&p.getCurrency().equals(r.currency())&&p.getPaymentDate().equals(r.paymentDate())&&Objects.equals(p.getOriginalPaymentId(),r.originalPaymentId())&&("REFUND".equals(p.getType())||(Objects.equals(p.getSubscriptionId(),r.subscriptionId())&&Objects.equals(p.getCoverageStart(),r.coverageStart())&&Objects.equals(p.getCoverageEnd(),r.coverageEnd())));}
 private AiPaymentVO toVO(AiPayment p){return new AiPaymentVO(p.getId(),p.getAccountId(),p.getSubscriptionId(),p.getType(),p.getAmount(),p.getCurrency(),p.getPaymentDate(),p.getCoverageStart(),p.getCoverageEnd(),p.getOriginalPaymentId(),p.getRequestKey(),p.getNote(),p.getRecordedAt());}
}
