package com.gak.aisubscription.service;
import com.gak.aisubscription.domain.*;
import com.gak.aisubscription.vo.*;
import java.time.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
/** 原币汇总：现金与成本分离，退款冲回原覆盖周期，未知费用不伪装成零。 */
@Service
public class AiSummaryService {
 public static final ZoneId DEFAULT_ZONE=ZoneId.of("Asia/Shanghai");
 private final AiPaymentService payments;private final AiSubscriptionService subscriptions;private final AiUsageService usage;private final AiAccountService accounts;
 public AiSummaryService(AiPaymentService payments,AiSubscriptionService subscriptions,AiUsageService usage,AiAccountService accounts){this.payments=payments;this.subscriptions=subscriptions;this.usage=usage;this.accounts=accounts;}
 public AiSummaryVO summary(Long owner,String month){
  return calculate(input(owner), parseMonth(month));
 }
 public AiInsightsVO insights(Long owner, String month) {
  YearMonth selected = parseMonth(month);
  AiAccountingInput input = input(owner);
  List<AiSummaryVO> trend = java.util.stream.IntStream.rangeClosed(0, 5)
      .mapToObj(index -> calculate(input, selected.minusMonths(5L - index))).toList();
  List<AiToolCostsVO> tools = input.accounts().stream().map(AiAccountVO::toolCode).distinct().sorted().map(code -> {
   AiSummaryVO report = calculate(input.forTool(code), selected);
   return new AiToolCostsVO(code, report.currencies().stream().filter(group -> group.confirmedCost() != null).toList(),
       report.complete());
  }).filter(tool -> !tool.currencies().isEmpty()).toList();
  return new AiInsightsVO(trend, tools);
 }
 private YearMonth parseMonth(String month) {
  try { return month == null ? YearMonth.now(DEFAULT_ZONE) : YearMonth.parse(month); }
  catch (DateTimeException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"月份格式应为 YYYY-MM"); }
 }
 private AiAccountingInput input(Long owner) {
  return new AiAccountingInput(payments.records(owner), subscriptions.records(owner), usage.records(owner), accounts.list(owner));
 }
 private AiSummaryVO calculate(AiAccountingInput input, YearMonth selected) {
  LocalDate from=selected.atDay(1),to=selected.plusMonths(1).atDay(1);
  List<AiPayment> paid=input.payments();List<AiSubscription> plans=input.subscriptions();List<AiUsageRecord> observations=input.usage();List<AiAccountVO> owned=input.accounts();
  Map<String,AiCurrencyTotals> totals=new TreeMap<>();Set<String> missing=new LinkedHashSet<>();
  addPayments(paid,totals,from,to);addUsage(observations,totals,missing,from,to);addSubscriptions(plans,paid,owned,totals,missing,from,to);
  if(totals.size()>1||totals.keySet().stream().anyMatch(c->!"CNY".equals(c)))missing.add("按原币分别展示，未提供汇率，未计算人民币合计");
  LocalDateTime updated=java.util.stream.Stream.concat(paid.stream().map(AiPayment::getRecordedAt),java.util.stream.Stream.concat(plans.stream().map(AiSubscription::getRecordedAt),observations.stream().map(AiUsageRecord::getRecordedAt))).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
  return new AiSummaryVO(selected.toString(),!owned.isEmpty()||!paid.isEmpty()||!observations.isEmpty(),totals.entrySet().stream().map(e->e.getValue().toVO(e.getKey())).toList(),missing.isEmpty(),List.copyOf(missing),updated);
 }
 private void addPayments(List<AiPayment> paid,Map<String,AiCurrencyTotals> totals,LocalDate from,LocalDate to){
  for(AiPayment p:paid){
   BigDecimal signed="REFUND".equals(p.getType())?p.getAmount().negate():p.getAmount();
   boolean cash=!p.getPaymentDate().isBefore(from)&&p.getPaymentDate().isBefore(to);
   boolean cost=p.getCoverageStart()!=null&&p.getCoverageEnd()!=null&&AiRules.overlap(p.getCoverageStart(),p.getCoverageEnd(),from,to);
   if(!cash&&!cost)continue;
   AiCurrencyTotals target=totals.computeIfAbsent(p.getCurrency(),c->new AiCurrencyTotals());
   if(cash)target.addCash(signed);
   // 只有订阅支付/其退款包含覆盖日期；充值、后付费结算不重复增加成本。
   if(cost)target.addCost(AiRules.amortize(signed,p.getCoverageStart(),p.getCoverageEnd(),from,to));
  }
 }
 private void addUsage(List<AiUsageRecord> records,Map<String,AiCurrencyTotals> totals,Set<String> missing,LocalDate from,LocalDate to){
  Instant monthStart=from.atStartOfDay(DEFAULT_ZONE).toInstant(),monthEnd=to.atStartOfDay(DEFAULT_ZONE).toInstant();
  for(AiUsageRecord u:records){
   if(!"API".equals(u.getKind())||"SUPERSEDED".equals(u.getStatus())||"WITHDRAWN".equals(u.getStatus())||"EXCLUDED".equals(u.getStatus()))continue;
   ZoneId zone=ZoneId.of(u.getZoneId());Instant start=u.getStartDate().atStartOfDay(zone).toInstant(),end=u.getEndDate().atStartOfDay(zone).toInstant();
   if(!start.isBefore(monthEnd)||!monthStart.isBefore(end))continue;
   if("REVIEW".equals(u.getStatus())){missing.add("存在待核对用量，已排除合计");continue;}
   if(start.isBefore(monthStart)||end.isAfter(monthEnd)){missing.add("存在跨月或跨时区边界用量，未按天猜测分摊费用");continue;}
   if(u.getCost()==null){missing.add("存在未记录费用的 API 用量");continue;}
   totals.computeIfAbsent(u.getCurrency(),c->new AiCurrencyTotals()).addCost(u.getCost());
  }
 }
 private void addSubscriptions(List<AiSubscription> plans,List<AiPayment> paid,List<AiAccountVO> accounts,Map<String,AiCurrencyTotals> totals,Set<String> missing,LocalDate from,LocalDate to){
  Set<Long> active=new HashSet<>();accounts.stream().filter(a->!Boolean.TRUE.equals(a.archived())).forEach(a->active.add(a.id()));
  LocalDate today=LocalDate.now(DEFAULT_ZONE);
  for(AiSubscription s:plans){
   if(Boolean.TRUE.equals(s.getTerminated())||!active.contains(s.getAccountId()))continue;
   boolean current=!today.isBefore(s.getStartDate())&&today.isBefore(s.getEndDate());
   boolean monthly=AiRules.overlap(s.getStartDate(),s.getEndDate(),from,to);
   if(!monthly&&!(current&&Boolean.TRUE.equals(s.getAutoRenew())))continue;
   AiCurrencyTotals target=totals.computeIfAbsent(s.getCurrency(),c->new AiCurrencyTotals());
   if(current&&Boolean.TRUE.equals(s.getAutoRenew())){
    if(s.getCycleMonths()>0)target.addCommitment(s.getAmount().divide(BigDecimal.valueOf(s.getCycleMonths()),12,RoundingMode.HALF_UP));
    else missing.add("自定义周期未折算月均订阅承诺");
   }
   if(monthly){
    BigDecimal estimate=AiRules.amortize(s.getAmount(),s.getStartDate(),s.getEndDate(),from,to);
    for(AiPayment p:paid){
     if(!"SUBSCRIPTION".equals(p.getType())||!Objects.equals(p.getSubscriptionId(),s.getId()))continue;
     LocalDate left=p.getCoverageStart().isAfter(from)?p.getCoverageStart():from,right=p.getCoverageEnd().isBefore(to)?p.getCoverageEnd():to;
     if(left.isBefore(right))estimate=estimate.subtract(AiRules.amortize(s.getAmount(),s.getStartDate(),s.getEndDate(),left,right));
    }
    target.addEstimated(estimate.max(BigDecimal.ZERO));
   }
  }
 }
}
