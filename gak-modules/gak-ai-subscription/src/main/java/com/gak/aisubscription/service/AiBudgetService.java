package com.gak.aisubscription.service;

import com.gak.aisubscription.domain.AiBudget;
import com.gak.aisubscription.dto.AiBudgetRequest;
import com.gak.aisubscription.mapper.AiIntegrationMapper;
import com.gak.aisubscription.vo.*;
import com.gak.framework.message.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 预算按原币确认成本判断；站内通知使用稳定业务键去重。 */
@Service
public class AiBudgetService {
    private final AiIntegrationMapper mapper;
    private final AiAccessService access;
    private final AiSummaryService summary;
    private final AiSubscriptionService subscriptions;
    private final MessagePublisher messages;

    public AiBudgetService(AiIntegrationMapper mapper, AiAccessService access, AiSummaryService summary,
            AiSubscriptionService subscriptions, MessagePublisher messages) {
        this.mapper = mapper; this.access = access; this.summary = summary;
        this.subscriptions = subscriptions; this.messages = messages;
    }

    public List<AiBudgetVO> list(Long owner, String month) {
        List<AiBudget> budgets = mapper.budgets(owner, month);
        if (budgets.isEmpty()) { return List.of(); }
        AiSummaryVO report = summary.summary(owner, month);
        return budgets.stream().map(budget -> {
            BigDecimal cost = report.currencies().stream().filter(group -> group.currency().equals(budget.currency()))
                    .map(AiCurrencySummaryVO::confirmedCost).filter(Objects::nonNull).findFirst().orElse(null);
            return new AiBudgetVO(month, budget.currency(), budget.amount(), budget.threshold(), cost, report.complete());
        }).toList();
    }

    @Transactional
    public void save(Long owner, AiBudgetRequest request) {
        access.lockOwner(owner);
        try { YearMonth.parse(request.month()); Currency.getInstance(request.currency()); }
        catch (DateTimeException | IllegalArgumentException ex) { AiRules.check(false, "月份或币种无效"); }
        mapper.saveBudget(new AiBudget(owner, request.month(), request.currency(), request.amount(), request.threshold()));
    }

    @Transactional
    public void checkReminders(Long owner) {
        access.lockOwner(owner);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        var activeAccounts = subscriptions.activeAccountIds(owner);
        for (var plan : subscriptions.records(owner)) {
            if (Boolean.TRUE.equals(plan.getTerminated()) || !activeAccounts.contains(plan.getAccountId())) { continue; }
            if (Boolean.TRUE.equals(plan.getAutoRenew())) {
                remind(owner, plan.getId(), plan.getNextBillingDate(), plan.getReminderDays(), today, "续费", plan.getPlanName());
            }
            remind(owner, plan.getId(), plan.getTrialEndDate(), plan.getReminderDays(), today, "试用结束", plan.getPlanName());
        }
        for (var budget : list(owner, YearMonth.from(today).toString())) {
            if (budget.confirmedCost() != null && budget.confirmedCost().compareTo(budget.amount().multiply(budget.threshold())) >= 0) {
                publish(owner, "budget:" + budget.month() + ":" + budget.currency() + ":" + budget.threshold(), "智订预算提醒",
                        budget.month() + " 已确认成本 " + budget.currency() + " " + budget.confirmedCost() + " 已达到预算提醒阈值。");
            }
        }
    }

    private void remind(Long owner, Long id, LocalDate date, Integer days, LocalDate today, String event, String name) {
        if (date != null && days != null && !date.isBefore(today) && !date.isAfter(today.plusDays(days))) {
            publish(owner, id + ":" + event + ":" + date, "智订 · " + event + "提醒", name + "：" + date + " " + event + "。");
        }
    }

    private void publish(Long owner, String event, String title, String body) {
        messages.publish(new PublishMessageCommand("AI_SUBSCRIPTION", owner + ":" + event, null,
                MessageAudience.USERS, List.of(owner), MessageCategory.BUSINESS, MessagePriority.NORMAL,
                title, body, MessageTarget.HOME));
    }
}
