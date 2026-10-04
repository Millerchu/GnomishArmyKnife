package com.gak.personalbills.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.framework.dictionary.DataDictionaryUsageSupport;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.PagedResult;
import com.gak.personalbills.domain.PersonalBill;
import com.gak.personalbills.domain.PersonalBudget;
import com.gak.personalbills.dto.AnnualBudgetQueryRequest;
import com.gak.personalbills.dto.PersonalBillQueryRequest;
import com.gak.personalbills.dto.PersonalBillSummaryQueryRequest;
import com.gak.personalbills.dto.SaveAnnualBudgetRequest;
import com.gak.personalbills.dto.SavePersonalBillRequest;
import com.gak.personalbills.mapper.PersonalBillMapper;
import com.gak.personalbills.mapper.PersonalBudgetMapper;
import com.gak.personalbills.vo.AnnualBudgetVO;
import com.gak.personalbills.vo.PersonalBillCategoryDistributionVO;
import com.gak.personalbills.vo.PersonalBillSummaryVO;
import com.gak.personalbills.vo.PersonalBillVO;
import com.gak.personalbills.vo.PersonalBudgetProgressVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 个人账单服务。
 */
@Service
public class PersonalBillService {

    private static final String APP_CODE = "APP_PERSONAL_BILLS";
    private static final String MODULE_CODE = "PERSONAL_BILLS";
    private static final String BILL_CATEGORY_FIELD = "categoryName";
    private static final String BUDGET_CATEGORY_FIELD = "budgetCategoryName";
    private static final String PAYMENT_METHOD_FIELD = "paymentMethod";
    private static final List<String> ALLOWED_BILL_TYPES = List.of("EXPENSE", "INCOME");
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal ZERO_RATIO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int RECENT_BILL_LIMIT = 5;

    private final PersonalBillMapper personalBillMapper;
    private final PersonalBudgetMapper personalBudgetMapper;
    private final UserMapper userMapper;
    private final DataDictionaryUsageSupport dataDictionaryUsageSupport;

    public PersonalBillService(PersonalBillMapper personalBillMapper,
                               PersonalBudgetMapper personalBudgetMapper,
                               UserMapper userMapper,
                               DataDictionaryUsageSupport dataDictionaryUsageSupport) {
        this.personalBillMapper = personalBillMapper;
        this.personalBudgetMapper = personalBudgetMapper;
        this.userMapper = userMapper;
        this.dataDictionaryUsageSupport = dataDictionaryUsageSupport;
    }

    /**
     * 分页查询账单。
     */
    public PagedResult<PersonalBillVO> page(Long currentUserId, PersonalBillQueryRequest request) {
        ensureCurrentUserExists(currentUserId);
        QueryWrapper<PersonalBill> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", currentUserId);
        applyBillFilters(wrapper, request);
        wrapper.orderByDesc("bill_date").orderByDesc("updated_at").orderByDesc("id");
        List<PersonalBill> allMatched = personalBillMapper.selectList(wrapper);

        long total = allMatched.size();
        int pageSize = Math.toIntExact(request.getPageSize());
        long maxPageNo = Math.max(1, (total + pageSize - 1) / pageSize);
        int pageNo = (int) Math.min(request.getPageNo(), maxPageNo);
        int fromIndex = Math.max(0, (pageNo - 1) * pageSize);
        int toIndex = Math.min(allMatched.size(), fromIndex + pageSize);
        List<PersonalBillVO> list = fromIndex >= toIndex
                ? List.of()
                : allMatched.subList(fromIndex, toIndex).stream().map(this::toBillVO).toList();
        return new PagedResult<>(list, total);
    }

    /**
     * 新增账单。
     */
    @Transactional
    public PersonalBillVO create(Long currentUserId, SavePersonalBillRequest request) {
        ensureCurrentUserExists(currentUserId);
        NormalizedBill normalized = normalizeBillRequest(request);
        PersonalBill bill = new PersonalBill();
        bill.setOwnerUserId(currentUserId);
        applyNormalizedBill(bill, normalized);
        bill.setCurrency(normalizeCurrency(request.getCurrency()));
        LocalDateTime now = LocalDateTime.now();
        bill.setCreatedAt(now);
        bill.setUpdatedAt(now);
        personalBillMapper.insert(bill);
        return toBillVO(bill);
    }

    /**
     * 更新账单。
     */
    @Transactional
    public PersonalBillVO update(Long currentUserId, Long id, SavePersonalBillRequest request) {
        ensureCurrentUserExists(currentUserId);
        PersonalBill current = getOwnedBillOrThrow(currentUserId, id);
        ensureBillIsEditable(current);
        NormalizedBill normalized = normalizeBillRequest(request);
        applyNormalizedBill(current, normalized);
        current.setCurrency(normalizeCurrency(request.getCurrency()));
        current.setUpdatedAt(LocalDateTime.now());
        personalBillMapper.updateById(current);
        return toBillVO(current);
    }

    /**
     * 删除账单。
     */
    @Transactional
    public void delete(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        PersonalBill current = getOwnedBillOrThrow(currentUserId, id);
        ensureBillIsEditable(current);
        personalBillMapper.deleteById(current.getId());
    }

    /**
     * 查询概览。
     */
    public PersonalBillSummaryVO getSummary(Long currentUserId, PersonalBillSummaryQueryRequest request) {
        ensureCurrentUserExists(currentUserId);
        YearMonth targetMonth = resolveTargetMonth(request.getMonth());
        int targetYear = request.getYear() != null ? request.getYear() : targetMonth.getYear();

        String currency = normalizeCurrency(request.getCurrency());
        List<PersonalBill> allBills = loadAllBills(currentUserId, currency);
        List<PersonalBudget> yearBudgets = "CNY".equals(currency)
                ? loadBudgetsByYear(currentUserId, targetYear) : List.of();

        LocalDate monthStart = targetMonth.atDay(1);
        LocalDate monthEndExclusive = targetMonth.plusMonths(1).atDay(1);
        List<PersonalBill> monthBills = allBills.stream()
                .filter(item -> !item.getBillDate().isBefore(monthStart) && item.getBillDate().isBefore(monthEndExclusive))
                .toList();
        List<PersonalBill> yearBills = allBills.stream()
                .filter(item -> item.getBillDate().getYear() == targetYear)
                .toList();

        BigDecimal currentMonthExpense = sumAmount(filterBillsByType(monthBills, "EXPENSE"));
        BigDecimal currentMonthIncome = sumAmount(filterBillsByType(monthBills, "INCOME"));
        BigDecimal currentYearExpense = sumAmount(filterBillsByType(yearBills, "EXPENSE"));
        BigDecimal annualBudgetAmount = sumBudgetLimit(yearBudgets);
        List<PersonalBudgetProgressVO> budgetProgressList = buildBudgetProgressList(yearBudgets, yearBills, targetYear);
        BigDecimal annualBudgetUsed = budgetProgressList.stream()
                .map(PersonalBudgetProgressVO::getUsedAmount)
                .reduce(ZERO_MONEY, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        PersonalBillSummaryVO summary = new PersonalBillSummaryVO();
        summary.setCurrentMonthExpense(currentMonthExpense);
        summary.setCurrentMonthIncome(currentMonthIncome);
        summary.setCurrentMonthBalance(currentMonthIncome.subtract(currentMonthExpense).setScale(2, RoundingMode.HALF_UP));
        summary.setCurrentYearExpense(currentYearExpense);
        summary.setAnnualBudgetAmount(annualBudgetAmount);
        summary.setAnnualBudgetUsed(annualBudgetUsed);
        summary.setAnnualBudgetRemaining(annualBudgetAmount.subtract(annualBudgetUsed).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        summary.setAnnualBudgetUsageRate(calculateRatio(annualBudgetUsed, annualBudgetAmount));
        summary.setCategoryDistribution(buildCategoryDistribution(monthBills));
        summary.setRecentBills(allBills.stream().limit(RECENT_BILL_LIMIT).map(this::toBillVO).toList());
        summary.setBudgetProgressList(budgetProgressList);
        return summary;
    }

    /**
     * 查询预算列表。
     */
    public List<AnnualBudgetVO> listBudgets(Long currentUserId, AnnualBudgetQueryRequest request) {
        ensureCurrentUserExists(currentUserId);
        int year = request.getYear() == null ? LocalDate.now().getYear() : request.getYear();
        return loadBudgetsByYear(currentUserId, year).stream()
                .map(this::toBudgetVO)
                .toList();
    }

    /**
     * 新增预算。
     */
    @Transactional
    public AnnualBudgetVO createBudget(Long currentUserId, SaveAnnualBudgetRequest request) {
        ensureCurrentUserExists(currentUserId);
        NormalizedBudget normalized = normalizeBudgetRequest(request);
        PersonalBudget budget = new PersonalBudget();
        budget.setOwnerUserId(currentUserId);
        applyNormalizedBudget(budget, normalized);
        LocalDateTime now = LocalDateTime.now();
        budget.setCreatedAt(now);
        budget.setUpdatedAt(now);
        personalBudgetMapper.insert(budget);
        return toBudgetVO(budget);
    }

    /**
     * 更新预算。
     */
    @Transactional
    public AnnualBudgetVO updateBudget(Long currentUserId, Long id, SaveAnnualBudgetRequest request) {
        ensureCurrentUserExists(currentUserId);
        PersonalBudget current = getOwnedBudgetOrThrow(currentUserId, id);
        NormalizedBudget normalized = normalizeBudgetRequest(request);
        applyNormalizedBudget(current, normalized);
        current.setUpdatedAt(LocalDateTime.now());
        personalBudgetMapper.updateById(current);
        return toBudgetVO(current);
    }

    /**
     * 删除预算。
     */
    @Transactional
    public void deleteBudget(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        PersonalBudget current = getOwnedBudgetOrThrow(currentUserId, id);
        personalBudgetMapper.deleteById(current.getId());
    }

    private void applyBillFilters(QueryWrapper<PersonalBill> wrapper, PersonalBillQueryRequest request) {
        wrapper.eq("currency", normalizeCurrency(request.getCurrency()));
        if (request.getTravelRecordId() != null) {
            wrapper.eq("source_type", "TRAVEL_RECORD").eq("source_id", request.getTravelRecordId());
        }
        if (StringUtils.hasText(request.getMonth())) {
            YearMonth yearMonth = resolveTargetMonth(request.getMonth());
            wrapper.ge("bill_date", yearMonth.atDay(1))
                    .lt("bill_date", yearMonth.plusMonths(1).atDay(1));
        }
        if (StringUtils.hasText(request.getBillType())) {
            wrapper.eq("bill_type", normalizeBillType(request.getBillType()));
        }
        if (StringUtils.hasText(request.getCategoryName())) {
            String categoryName = dataDictionaryUsageSupport.normalizeValueByUsage(
                    APP_CODE,
                    MODULE_CODE,
                    BILL_CATEGORY_FIELD,
                    request.getCategoryName(),
                    false
            );
            wrapper.eq("category_name", categoryName);
        }
        if (StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            wrapper.and(nested -> nested.like("category_name", keyword)
                    .or().like("account_name", keyword)
                    .or().like("payment_method", keyword)
                    .or().like("merchant_name", keyword)
                    .or().like("note", keyword));
        }
    }

    private List<PersonalBill> loadAllBills(Long currentUserId, String currency) {
        QueryWrapper<PersonalBill> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", currentUserId)
                .eq("currency", currency)
                .orderByDesc("bill_date")
                .orderByDesc("updated_at")
                .orderByDesc("id");
        return personalBillMapper.selectList(wrapper);
    }

    private List<PersonalBudget> loadBudgetsByYear(Long currentUserId, int year) {
        QueryWrapper<PersonalBudget> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", currentUserId)
                .eq("budget_year", year)
                .orderByAsc("category_name")
                .orderByAsc("id");
        return personalBudgetMapper.selectList(wrapper);
    }

    private void ensureCurrentUserExists(Long currentUserId) {
        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前登录用户不存在");
        }
    }

    private PersonalBill getOwnedBillOrThrow(Long currentUserId, Long id) {
        PersonalBill bill = personalBillMapper.selectOwnedForUpdate(id, currentUserId);
        if (bill == null || !Objects.equals(bill.getOwnerUserId(), currentUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账单记录不存在");
        }
        return bill;
    }

    private PersonalBudget getOwnedBudgetOrThrow(Long currentUserId, Long id) {
        PersonalBudget budget = personalBudgetMapper.selectById(id);
        if (budget == null || !Objects.equals(budget.getOwnerUserId(), currentUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "预算记录不存在");
        }
        return budget;
    }

    private NormalizedBill normalizeBillRequest(SavePersonalBillRequest request) {
        String billType = normalizeBillType(request.getBillType());
        if (request.isRefund() && !"EXPENSE".equals(billType)) {
            throw new BusinessException("PERSONAL_BILL_REFUND_TYPE_INVALID", "退款冲减必须使用支出类型");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("PERSONAL_BILL_AMOUNT_INVALID", "请输入大于零的付款或退款金额");
        }
        String categoryName = dataDictionaryUsageSupport.normalizeValueByUsage(
                APP_CODE,
                MODULE_CODE,
                BILL_CATEGORY_FIELD,
                request.getCategoryName(),
                true
        );
        String paymentMethod = dataDictionaryUsageSupport.normalizeValueByUsage(
                APP_CODE,
                MODULE_CODE,
                PAYMENT_METHOD_FIELD,
                request.getPaymentMethod(),
                false
        );
        return new NormalizedBill(
                billType,
                categoryName,
                request.isRefund() ? scaleMoney(request.getAmount()).negate() : scaleMoney(request.getAmount()),
                trimToNull(request.getAccountName()),
                paymentMethod,
                trimToNull(request.getMerchantName()),
                request.getBillDate(),
                trimToNull(request.getNote())
        );
    }

    private NormalizedBudget normalizeBudgetRequest(SaveAnnualBudgetRequest request) {
        String categoryName = dataDictionaryUsageSupport.normalizeValueByUsage(
                APP_CODE,
                MODULE_CODE,
                BUDGET_CATEGORY_FIELD,
                request.getCategoryName(),
                true
        );
        return new NormalizedBudget(
                request.getYear(),
                StringUtils.hasText(request.getBudgetName()) ? request.getBudgetName().trim() : categoryName,
                categoryName,
                scaleMoney(request.getAnnualLimit()),
                scaleRatio(request.getAlertThreshold()),
                trimToNull(request.getNote())
        );
    }

    private void applyNormalizedBill(PersonalBill bill, NormalizedBill normalized) {
        bill.setBillType(normalized.billType());
        bill.setCategoryName(normalized.categoryName());
        bill.setAmount(normalized.amount());
        bill.setAccountName(normalized.accountName());
        bill.setPaymentMethod(normalized.paymentMethod());
        bill.setMerchantName(normalized.merchantName());
        bill.setBillDate(normalized.billDate());
        bill.setNote(normalized.note());
    }

    private void applyNormalizedBudget(PersonalBudget budget, NormalizedBudget normalized) {
        budget.setBudgetName(normalized.budgetName());
        budget.setBudgetYear(normalized.year());
        budget.setCategoryName(normalized.categoryName());
        budget.setAnnualLimit(normalized.annualLimit());
        budget.setAlertThreshold(normalized.alertThreshold());
        budget.setNote(normalized.note());
    }

    private List<PersonalBill> filterBillsByType(List<PersonalBill> bills, String billType) {
        return bills.stream().filter(item -> billType.equals(item.getBillType())).toList();
    }

    private BigDecimal sumAmount(List<PersonalBill> bills) {
        return bills.stream()
                .map(PersonalBill::getAmount)
                .filter(Objects::nonNull)
                .reduce(ZERO_MONEY, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumBudgetLimit(List<PersonalBudget> budgets) {
        return budgets.stream()
                .map(PersonalBudget::getAnnualLimit)
                .filter(Objects::nonNull)
                .reduce(ZERO_MONEY, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private List<PersonalBillCategoryDistributionVO> buildCategoryDistribution(List<PersonalBill> monthBills) {
        List<PersonalBill> expenseBills = filterBillsByType(monthBills, "EXPENSE");
        BigDecimal totalExpense = sumAmount(expenseBills);
        Map<String, BigDecimal> categoryAmountMap = new LinkedHashMap<>();
        for (PersonalBill item : expenseBills) {
            categoryAmountMap.merge(item.getCategoryName(), item.getAmount(), BigDecimal::add);
        }

        List<PersonalBillCategoryDistributionVO> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : categoryAmountMap.entrySet()) {
            PersonalBillCategoryDistributionVO vo = new PersonalBillCategoryDistributionVO();
            vo.setCategoryName(entry.getKey());
            vo.setAmount(entry.getValue().setScale(2, RoundingMode.HALF_UP));
            vo.setRatio(calculateRatio(entry.getValue(), totalExpense));
            result.add(vo);
        }
        result.sort(Comparator.comparing(PersonalBillCategoryDistributionVO::getAmount).reversed());
        return result;
    }

    private List<PersonalBudgetProgressVO> buildBudgetProgressList(List<PersonalBudget> budgets,
                                                                   List<PersonalBill> yearBills,
                                                                   int year) {
        List<PersonalBill> expenseBills = filterBillsByType(yearBills, "EXPENSE");
        List<PersonalBudgetProgressVO> result = new ArrayList<>();
        // 同类预算先合计，避免一笔消费在多条预算中重复计入已用额度。
        Map<String, List<PersonalBudget>> grouped = new LinkedHashMap<>();
        budgets.forEach(item -> grouped.computeIfAbsent(item.getCategoryName(), key -> new ArrayList<>()).add(item));
        for (List<PersonalBudget> categoryBudgets : grouped.values()) {
            PersonalBudget item = categoryBudgets.get(0);
            BigDecimal usedAmount = expenseBills.stream()
                    .filter(bill -> Objects.equals(bill.getCategoryName(), item.getCategoryName()))
                    .map(PersonalBill::getAmount)
                    .reduce(ZERO_MONEY, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal annualLimit = sumBudgetLimit(categoryBudgets);
            BigDecimal usageRate = calculateRatio(usedAmount, annualLimit);

            PersonalBudgetProgressVO vo = new PersonalBudgetProgressVO();
            vo.setId(item.getId());
            vo.setYear(year);
            vo.setCategoryName(item.getCategoryName());
            vo.setAnnualLimit(annualLimit);
            // 多个明细使用金额加权阈值，保持各项预警金额的总和。
            BigDecimal alertAmount = categoryBudgets.stream()
                    .map(budget -> budget.getAnnualLimit().multiply(scaleRatio(budget.getAlertThreshold())))
                    .reduce(ZERO_MONEY, BigDecimal::add);
            vo.setAlertThreshold(calculateRatio(alertAmount, annualLimit));
            vo.setNote(item.getNote());
            vo.setUsedAmount(usedAmount);
            vo.setRemainingAmount(annualLimit.subtract(usedAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            vo.setUsageRate(usageRate.multiply(ONE_HUNDRED).setScale(2, RoundingMode.HALF_UP));
            applyBudgetStatus(vo, usageRate, vo.getAlertThreshold());
            result.add(vo);
        }
        result.sort(Comparator.comparing(PersonalBudgetProgressVO::getUsageRate).reversed()
                .thenComparing(PersonalBudgetProgressVO::getCategoryName));
        return result;
    }

    private void applyBudgetStatus(PersonalBudgetProgressVO target, BigDecimal usageRate, BigDecimal alertThreshold) {
        if (usageRate.compareTo(BigDecimal.ONE) >= 0) {
            target.setStatusClass("danger");
            target.setStatusText("已超预算");
            return;
        }
        if (usageRate.compareTo(alertThreshold) >= 0) {
            target.setStatusClass("warning");
            target.setStatusText("接近上限");
            return;
        }
        target.setStatusClass("safe");
        target.setStatusText("预算健康");
    }

    private PersonalBillVO toBillVO(PersonalBill bill) {
        PersonalBillVO vo = new PersonalBillVO();
        vo.setId(bill.getId());
        vo.setCurrency(bill.getCurrency());
        vo.setSourceType(bill.getSourceType());
        vo.setSourceId(bill.getSourceId());
        vo.setSourceEntry(bill.getSourceEntry());
        vo.setBillType(bill.getBillType());
        vo.setCategoryName(bill.getCategoryName());
        vo.setAmount(scaleMoney(bill.getAmount()));
        vo.setAccountName(bill.getAccountName());
        vo.setPaymentMethod(bill.getPaymentMethod());
        vo.setMerchantName(bill.getMerchantName());
        vo.setBillDate(bill.getBillDate());
        vo.setNote(bill.getNote());
        vo.setUpdatedAt(bill.getUpdatedAt());
        return vo;
    }

    private AnnualBudgetVO toBudgetVO(PersonalBudget budget) {
        AnnualBudgetVO vo = new AnnualBudgetVO();
        vo.setId(budget.getId());
        vo.setBudgetName(budget.getBudgetName());
        vo.setYear(budget.getBudgetYear());
        vo.setCategoryName(budget.getCategoryName());
        vo.setAnnualLimit(scaleMoney(budget.getAnnualLimit()));
        vo.setAlertThreshold(scaleRatio(budget.getAlertThreshold()));
        vo.setNote(budget.getNote());
        return vo;
    }

    private void ensureBillIsEditable(PersonalBill bill) {
        if (bill.getSourceType() != null || bill.getSourceId() != null) {
            String sourceName = switch (bill.getSourceType() == null ? "" : bill.getSourceType()) {
                case "HEALTH_VISIT" -> "健康就诊记录";
                case "FUEL_RECORD" -> "油耗统计";
                default -> "个人行程记录";
            };
            throw new BusinessException("PERSONAL_BILL_SOURCE_LINKED", "该账单由" + sourceName + "管理，请前往来源记录修改或删除");
        }
    }

    private String normalizeCurrency(String value) {
        String code = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "CNY";
        try {
            Currency.getInstance(code);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("PERSONAL_BILL_CURRENCY_INVALID", "请输入有效的 ISO 币种代码");
        }
        return code;
    }

    private String normalizeBillType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException("PERSONAL_BILL_TYPE_REQUIRED", "账单类型不能为空");
        }
        String upperValue = normalized.toUpperCase(Locale.ROOT);
        if (!ALLOWED_BILL_TYPES.contains(upperValue)) {
            throw new BusinessException("PERSONAL_BILL_TYPE_INVALID", "账单类型不合法");
        }
        return upperValue;
    }

    private YearMonth resolveTargetMonth(String month) {
        if (!StringUtils.hasText(month)) {
            return YearMonth.now();
        }
        return YearMonth.parse(month.trim());
    }

    private BigDecimal calculateRatio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO_RATIO;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return value == null ? ZERO_MONEY : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleRatio(BigDecimal value) {
        return value == null ? BigDecimal.valueOf(0.80).setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record NormalizedBill(String billType,
                                  String categoryName,
                                  BigDecimal amount,
                                  String accountName,
                                  String paymentMethod,
                                  String merchantName,
                                  LocalDate billDate,
                                  String note) {
    }

    private record NormalizedBudget(Integer year,
                                    String budgetName,
                                    String categoryName,
                                    BigDecimal annualLimit,
                                    BigDecimal alertThreshold,
                                    String note) {
    }
}
