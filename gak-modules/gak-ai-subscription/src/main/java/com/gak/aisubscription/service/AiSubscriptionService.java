package com.gak.aisubscription.service;
import com.gak.aisubscription.domain.AiSubscription;
import com.gak.aisubscription.dto.SaveAiSubscriptionRequest;
import com.gak.aisubscription.mapper.AiSubscriptionMapper;
import com.gak.aisubscription.vo.AiSubscriptionVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
/** 订阅配置可变，已确认支付的金额和覆盖周期独立保留。 */
@Service
public class AiSubscriptionService {
 private final AiSubscriptionMapper mapper;private final AiAccountService accounts;private final AiAccessService access;
 public AiSubscriptionService(AiSubscriptionMapper mapper,AiAccountService accounts,AiAccessService access){this.mapper=mapper;this.accounts=accounts;this.access=access;}
 public List<AiSubscriptionVO> list(Long owner){return records(owner).stream().map(this::toVO).toList();}
 public java.util.Set<Long> activeAccountIds(Long owner) {
  return accounts.list(owner).stream().filter(account -> !Boolean.TRUE.equals(account.archived()))
      .map(com.gak.aisubscription.vo.AiAccountVO::id).collect(java.util.stream.Collectors.toSet());
 }
 public List<AiSubscription> records(Long owner){return mapper.selectList(new QueryWrapper<AiSubscription>().eq("owner_user_id",owner).orderByAsc("next_billing_date").orderByDesc("id"));}
 public AiSubscription requireOwned(Long owner,Long id){AiSubscription s=mapper.selectById(id);if(s==null||!Objects.equals(owner,s.getOwnerUserId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"订阅不可用");return s;}
 @Transactional public AiSubscriptionVO save(Long owner,Long id,SaveAiSubscriptionRequest r){
  access.lockOwner(owner);accounts.requireActive(owner,r.accountId());AiRules.interval(r.startDate(),r.endDate());
  AiRules.check(r.nextBillingDate()==null||!r.nextBillingDate().isBefore(r.startDate()),"下次扣费日期不能早于开始日期");
  AiSubscription s=id==null?new AiSubscription():requireOwned(owner,id);
  if(id!=null){AiRules.check(s.getAccountId().equals(r.accountId()),"已有订阅不能转移账号");AiRules.check(!Boolean.TRUE.equals(s.getTerminated()),"已终止订阅请新增套餐");}
  s.setOwnerUserId(owner);s.setAccountId(r.accountId());s.setPlanName(r.planName().trim());s.setStartDate(r.startDate());s.setEndDate(r.endDate());s.setNextBillingDate(r.nextBillingDate());s.setCycleMonths(r.cycleMonths());s.setAmount(r.amount());s.setCurrency(r.currency());s.setAutoRenew(r.autoRenew());s.setTrialEndDate(r.trialEndDate());s.setReminderDays(r.reminderDays());s.setTerminated(false);s.setRecordedAt(LocalDateTime.now());
  if (Boolean.TRUE.equals(r.autoRenew()) && r.nextBillingDate() == null && r.cycleMonths() > 0) {
   s.setNextBillingDate(AiRules.billingOnOrAfter(r.startDate(), r.cycleMonths(), r.endDate()));
  }
  if(id==null)mapper.insert(s);else mapper.updateById(s);return toVO(s);
 }
 @Transactional public void terminate(Long owner,Long id){access.lockOwner(owner);AiSubscription s=requireOwned(owner,id);s.setTerminated(true);s.setAutoRenew(false);mapper.updateById(s);}
 private AiSubscriptionVO toVO(AiSubscription s){return new AiSubscriptionVO(s.getId(),s.getAccountId(),s.getPlanName(),s.getStartDate(),s.getEndDate(),s.getNextBillingDate(),s.getCycleMonths(),s.getAmount(),s.getCurrency(),s.getAutoRenew(),s.getTrialEndDate(),s.getReminderDays(),s.getTerminated(),s.getRecordedAt());}
}
