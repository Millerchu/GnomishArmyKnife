package com.gak.aisubscription.service;
import java.math.BigDecimal;
import com.gak.aisubscription.vo.AiCurrencySummaryVO;
/** 单一币种的聚合工作状态；不作为业务持久化模型。 */
public class AiCurrencyTotals {
 private BigDecimal confirmed, cash, commitment, estimated;
 public void addCost(BigDecimal amount) { confirmed = add(confirmed, amount); }
 public void addCash(BigDecimal amount) { cash = add(cash, amount); }
 public void addCommitment(BigDecimal amount) { commitment = add(commitment, amount); }
 public void addEstimated(BigDecimal amount) { estimated = add(estimated, amount); }
 public AiCurrencySummaryVO toVO(String currency) {
  return new AiCurrencySummaryVO(currency, display(confirmed), display(cash), display(commitment), display(estimated));
 }
 private static BigDecimal add(BigDecimal total, BigDecimal amount) {
  return total == null ? amount : total.add(amount);
 }
 private static BigDecimal display(BigDecimal amount) {
  return amount == null ? null : AiRules.display(amount);
 }
}
