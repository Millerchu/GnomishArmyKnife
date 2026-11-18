package com.gak.aisubscription.service;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
/** 日期与金额纯计算，所有区间采用左闭右开。 */
public final class AiRules {
 private AiRules() {}
 public static void check(boolean condition,String message) {if(!condition) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 public static void interval(LocalDate start,LocalDate end) {check(start!=null && end!=null && end.isAfter(start),"结束日期必须晚于开始日期（结束日不计入）");}
 public static void zone(String zone) {try {ZoneId.of(zone);} catch(DateTimeException|NullPointerException ex){check(false,"时区无效");}}
 public static boolean overlap(LocalDate a,LocalDate b,LocalDate c,LocalDate d) {return a.isBefore(d)&&c.isBefore(b);}
 public static BigDecimal amortize(BigDecimal amount,LocalDate start,LocalDate end,LocalDate from,LocalDate to) {
  interval(start,end);if(!overlap(start,end,from,to)) return BigDecimal.ZERO;
  LocalDate left=start.isAfter(from)?start:from,right=end.isBefore(to)?end:to;
  return amount.multiply(BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(left,right))).divide(BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(start,end)),12,RoundingMode.HALF_UP);
 }
 /** 从原始锚点计算第 N 次续费，绝不在已裁切的二月日期上递推。 */
 public static LocalDate billingDate(LocalDate anchor,int cycleMonths,int occurrence) {return anchor.plusMonths((long)cycleMonths*occurrence);}
 public static LocalDate billingOnOrAfter(LocalDate anchor, int cycleMonths, LocalDate boundary) {
  long months = java.time.temporal.ChronoUnit.MONTHS.between(anchor.withDayOfMonth(1), boundary.withDayOfMonth(1));
  int occurrence = Math.toIntExact(Math.max(1, months / cycleMonths));
  LocalDate date = billingDate(anchor, cycleMonths, occurrence);
  return date.isBefore(boundary) ? billingDate(anchor, cycleMonths, occurrence + 1) : date;
 }
 public static BigDecimal display(BigDecimal amount) {return amount.setScale(2,RoundingMode.HALF_UP);}
}
