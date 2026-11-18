package com.gak.aisubscription.service;

import com.gak.aisubscription.domain.*;
import com.gak.aisubscription.dto.SaveAiPaymentRequest;
import com.gak.aisubscription.enums.AiPaymentType;
import com.gak.aisubscription.mapper.AiPaymentMapper;
import com.gak.aisubscription.vo.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 金额/日期/重复观测与越权回归，避免把充值、累计或退款重复计费。 */
class AiAccountingTest {
 @Test void leapYearAmortizationAndMonthEndAnchor(){
  assertEquals(new BigDecimal("29.000000000000"),AiRules.amortize(new BigDecimal("366"),LocalDate.of(2024,1,1),LocalDate.of(2025,1,1),LocalDate.of(2024,2,1),LocalDate.of(2024,3,1)));
  assertEquals(LocalDate.of(2024,2,29),AiRules.billingDate(LocalDate.of(2024,1,31),1,1));
  assertEquals(LocalDate.of(2024,3,31),AiRules.billingDate(LocalDate.of(2024,1,31),1,2));
  assertEquals(LocalDate.of(2024,3,31),AiRules.billingOnOrAfter(
      LocalDate.of(2024,1,31),1,LocalDate.of(2024,3,30)));
 }
 @Test void cumulativeObservationsReplaceInsteadOfSum(){
  AiUsageRecord newest=usage(1L,"CUMULATIVE","MANUAL","TOKENS","10"),old=usage(2L,"CUMULATIVE","MANUAL","TOKENS","8");
  newest.setEndDate(LocalDate.of(2026,11,20));
  AiUsageService.classify(List.of(newest,old));
  assertEquals("INCLUDED",newest.getStatus());assertEquals("SUPERSEDED",old.getStatus());
 }
 @Test void incrementalOverlapsAreAllowedButCumulativeOverlapNeedsReview(){
  AiUsageRecord a=usage(1L,"INCREMENT","MANUAL","TOKENS","10"),b=usage(2L,"INCREMENT","MANUAL","TOKENS","8");
  AiUsageService.classify(List.of(a,b));assertEquals("INCLUDED",a.getStatus());assertEquals("INCLUDED",b.getStatus());
  b.setMode("CUMULATIVE");AiUsageService.classify(List.of(a,b));assertEquals("REVIEW",a.getStatus());assertEquals("REVIEW",b.getStatus());
 }
 @Test void officialCostExcludesManualCostAndExplicitSupplementRestoresIt(){
  AiUsageRecord official=usage(1L,"CUMULATIVE","OFFICIAL","COST","10"),manual=usage(2L,"INCREMENT","MANUAL","TOKENS","8");
  official.setStartDate(LocalDate.of(2026,11,10));
  AiUsageService.classify(List.of(official,manual));assertEquals("INCLUDED",official.getStatus());assertEquals("REVIEW",manual.getStatus());
  manual.setResolution("INDEPENDENT");AiUsageService.classify(List.of(official,manual));assertEquals("INCLUDED",manual.getStatus());
  official.setStatus("WITHDRAWN");manual.setResolution("NORMAL");AiUsageService.classify(List.of(official,manual));assertEquals("INCLUDED",manual.getStatus());assertEquals("WITHDRAWN",official.getStatus());
 }
 @Test void topupIsCashOnlyAndRefundReversesOriginalSubscriptionPeriod(){
  AiPayment annual=payment(1L,"SUBSCRIPTION","366",LocalDate.of(2024,1,1));annual.setCoverageStart(LocalDate.of(2024,1,1));annual.setCoverageEnd(LocalDate.of(2025,1,1));
  AiPayment refund=payment(2L,"REFUND","183",LocalDate.of(2024,5,1));refund.setOriginalPaymentId(1L);refund.setCoverageStart(annual.getCoverageStart());refund.setCoverageEnd(annual.getCoverageEnd());
  AiPayment topup=payment(3L,"TOPUP","100",LocalDate.of(2024,2,10));
  AiSummaryVO result=summary(List.of(annual,refund,topup),List.of()).summary(1L,"2024-02");
  assertEquals(new BigDecimal("14.50"),result.currencies().get(0).confirmedCost());assertEquals(new BigDecimal("100.00"),result.currencies().get(0).cashExpense());
 }
 @Test void unknownAndCrossMonthUsageAreNotInventedOrProrated(){
  AiUsageRecord unknown=usage(1L,"INCREMENT","MANUAL","TOKENS",null),cross=usage(2L,"INCREMENT","MANUAL","TOKENS","300");
  cross.setStartDate(LocalDate.of(2026,10,15));cross.setEndDate(LocalDate.of(2026,11,15));
  AiSummaryVO result=summary(List.of(),List.of(unknown,cross)).summary(1L,"2026-11");
  assertFalse(result.complete());assertTrue(result.currencies().isEmpty());assertEquals(2,result.missingReasons().size());
 }
 @Test void utcExpenseOverlapsTheFollowingShanghaiDay() {
  AiUsageRecord official=usage(1L,"CUMULATIVE","OFFICIAL","COST","10");
  official.setZoneId("UTC"); official.setEndDate(LocalDate.of(2026,11,2));
  AiUsageRecord manual=usage(2L,"INCREMENT","MANUAL","TOKENS","8");
  manual.setStartDate(LocalDate.of(2026,11,2)); manual.setEndDate(LocalDate.of(2026,11,3));
  AiUsageService.classify(List.of(official,manual));
  assertEquals("REVIEW",manual.getStatus());
 }
 @Test void mixedCurrenciesRemainSeparate(){
  AiPayment cny=payment(1L,"TOPUP","100",LocalDate.of(2026,11,10)),usd=payment(2L,"TOPUP","20",LocalDate.of(2026,11,10));usd.setCurrency("USD");
  AiSummaryVO result=summary(List.of(cny,usd),List.of()).summary(1L,"2026-11");assertEquals(2,result.currencies().size());assertFalse(result.complete());
  assertNull(result.currencies().getFirst().confirmedCost());
 }
 @Test void anotherUsersPaymentCannotBeReadOrRefunded(){
  AiPaymentMapper mapper=mock(AiPaymentMapper.class);AiPayment p=payment(1L,"TOPUP","10",LocalDate.of(2026,11,1));p.setOwnerUserId(2L);when(mapper.selectById(1L)).thenReturn(p);
  AiPaymentService service=new AiPaymentService(mapper,mock(AiAccountService.class),mock(AiSubscriptionService.class),mock(AiAccessService.class));
  assertEquals(404,assertThrows(ResponseStatusException.class,()->service.requireOwned(1L,1L)).getStatusCode().value());
 }
 @Test void aggregateRefundCannotExceedOriginalAndForeignCurrencyRejected(){
  AiPaymentMapper mapper=mock(AiPaymentMapper.class);AiPayment original=payment(1L,"TOPUP","100",LocalDate.of(2026,11,1));original.setOwnerUserId(1L);original.setAccountId(10L);
  AiPayment refunded=payment(2L,"REFUND","80",LocalDate.of(2026,11,2));refunded.setOriginalPaymentId(1L);
  when(mapper.selectById(1L)).thenReturn(original);when(mapper.selectList(any())).thenReturn(List.of(original,refunded));
  AiPaymentService service=new AiPaymentService(mapper,mock(AiAccountService.class),mock(AiSubscriptionService.class),mock(AiAccessService.class));
  SaveAiPaymentRequest request=new SaveAiPaymentRequest(10L,null,AiPaymentType.REFUND,new BigDecimal("21"),"CNY",LocalDate.of(2026,11,3),null,null,1L,"refund-1",null);
  assertThrows(ResponseStatusException.class,()->service.create(1L,request));verify(mapper,never()).insert(any(AiPayment.class));
  SaveAiPaymentRequest foreign=new SaveAiPaymentRequest(10L,null,AiPaymentType.REFUND,new BigDecimal("1"),"USD",LocalDate.of(2026,11,3),null,null,1L,"refund-2",null);
  assertThrows(ResponseStatusException.class,()->service.create(1L,foreign));
 }
 private AiSummaryService summary(List<AiPayment> paid,List<AiUsageRecord> records){
  AiPaymentService payments=mock(AiPaymentService.class);AiSubscriptionService plans=mock(AiSubscriptionService.class);AiUsageService usage=mock(AiUsageService.class);AiAccountService accounts=mock(AiAccountService.class);
  when(payments.records(1L)).thenReturn(paid);when(plans.records(1L)).thenReturn(List.of());when(usage.records(1L)).thenReturn(records);when(accounts.list(1L)).thenReturn(List.of());return new AiSummaryService(payments,plans,usage,accounts);
 }
 private AiPayment payment(Long id,String type,String amount,LocalDate date){AiPayment p=new AiPayment();p.setId(id);p.setType(type);p.setAmount(new BigDecimal(amount));p.setCurrency("CNY");p.setPaymentDate(date);return p;}
 private AiUsageRecord usage(Long id,String mode,String source,String metric,String cost){
  AiUsageRecord u=new AiUsageRecord();u.setId(id);u.setAccountId(10L);u.setKind("API");u.setMode(mode);u.setSource(source);u.setMetric(metric);u.setUnit("token");u.setDimension("");u.setPeriodKey("2026-11");u.setZoneId("Asia/Shanghai");u.setStartDate(LocalDate.of(2026,11,1));u.setEndDate(LocalDate.of(2026,11,15));u.setCost(cost==null?null:new BigDecimal(cost));u.setCurrency("CNY");u.setStatus("INCLUDED");u.setResolution("NORMAL");return u;
 }
}
