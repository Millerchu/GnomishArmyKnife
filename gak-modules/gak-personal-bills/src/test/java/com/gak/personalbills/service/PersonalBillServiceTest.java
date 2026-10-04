package com.gak.personalbills.service;

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
import com.gak.personalbills.vo.PersonalBillSummaryVO;
import com.gak.personalbills.vo.PersonalBillVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalBillServiceTest {

    @Mock
    private PersonalBillMapper personalBillMapper;

    @Mock
    private PersonalBudgetMapper personalBudgetMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private DataDictionaryUsageSupport dataDictionaryUsageSupport;

    @InjectMocks
    private PersonalBillService personalBillService;

    @BeforeEach
    void setUp() {
        lenient().when(dataDictionaryUsageSupport.normalizeValueByUsage(eq("APP_PERSONAL_BILLS"), eq("PERSONAL_BILLS"),
                anyString(), anyString(), anyBoolean()))
                .thenAnswer(invocation -> {
                    String value = invocation.getArgument(3);
                    boolean required = invocation.getArgument(4);
                    if ((value == null || value.trim().isEmpty()) && required) {
                        throw new BusinessException("DICT_ITEM_VALUE_REQUIRED", "字典值不能为空");
                    }
                    return value == null ? null : value.trim();
                });
    }

    @Test
    void pageShouldFilterAndReturnPagedResult() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        when(personalBillMapper.selectList(any())).thenReturn(List.of(
                buildBill(1001L, "EXPENSE", "餐饮", new BigDecimal("88.00"), LocalDate.of(2026, 5, 10)),
                buildBill(1002L, "INCOME", "工资", new BigDecimal("18000.00"), LocalDate.of(2026, 5, 5))
        ));

        PersonalBillQueryRequest request = new PersonalBillQueryRequest();
        request.setPageNo(1L);
        request.setPageSize(8L);

        PagedResult<PersonalBillVO> result = personalBillService.page(1L, request);

        assertEquals(2L, result.total());
        assertEquals(2, result.list().size());
        assertEquals("餐饮", result.list().get(0).getCategoryName());
    }

    @Test
    void createShouldPersistNormalizedBill() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        doAnswer(invocation -> {
            PersonalBill bill = invocation.getArgument(0);
            bill.setId(9001L);
            return 1;
        }).when(personalBillMapper).insert(any(PersonalBill.class));

        SavePersonalBillRequest request = new SavePersonalBillRequest();
        request.setBillType("expense");
        request.setCategoryName(" 餐饮 ");
        request.setAmount(new BigDecimal("88"));
        request.setAccountName(" 招商银行卡 ");
        request.setPaymentMethod(" 支付宝 ");
        request.setMerchantName(" 盒马鲜生 ");
        request.setBillDate(LocalDate.of(2026, 5, 10));
        request.setNote(" 晚餐 ");

        PersonalBillVO result = personalBillService.create(1L, request);

        ArgumentCaptor<PersonalBill> captor = ArgumentCaptor.forClass(PersonalBill.class);
        verify(personalBillMapper).insert(captor.capture());
        assertEquals("EXPENSE", captor.getValue().getBillType());
        assertEquals("餐饮", captor.getValue().getCategoryName());
        assertEquals("支付宝", captor.getValue().getPaymentMethod());
        assertEquals(new BigDecimal("88.00"), result.getAmount());
    }

    @Test
    void summaryShouldAggregateBudgetAndBills() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        when(personalBillMapper.selectList(any())).thenReturn(List.of(
                buildBill(1001L, "EXPENSE", "餐饮", new BigDecimal("88.00"), LocalDate.of(2026, 5, 10)),
                buildBill(1002L, "EXPENSE", "交通", new BigDecimal("36.00"), LocalDate.of(2026, 5, 8)),
                buildBill(1003L, "INCOME", "工资", new BigDecimal("18000.00"), LocalDate.of(2026, 5, 5))
        ));
        when(personalBudgetMapper.selectList(any())).thenReturn(List.of(
                buildBudget(2001L, 2026, "餐饮", new BigDecimal("15000.00"), new BigDecimal("0.80")),
                buildBudget(2002L, 2026, "交通", new BigDecimal("6000.00"), new BigDecimal("0.85")),
                buildBudget(2003L, 2026, "餐饮", new BigDecimal("1000.00"), new BigDecimal("0.80"))
        ));

        PersonalBillSummaryQueryRequest request = new PersonalBillSummaryQueryRequest();
        request.setMonth("2026-05");
        request.setYear(2026);

        PersonalBillSummaryVO result = personalBillService.getSummary(1L, request);

        assertEquals(new BigDecimal("124.00"), result.getCurrentMonthExpense());
        assertEquals(new BigDecimal("18000.00"), result.getCurrentMonthIncome());
        assertEquals(2, result.getBudgetProgressList().size());
        assertEquals(new BigDecimal("22000.00"), result.getAnnualBudgetAmount());
        assertEquals(new BigDecimal("124.00"), result.getAnnualBudgetUsed());
        assertEquals("餐饮", result.getCategoryDistribution().get(0).getCategoryName());
    }

    @Test
    void createBudgetShouldAllowMultipleItemsInSameCategory() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        SaveAnnualBudgetRequest request = new SaveAnnualBudgetRequest();
        request.setYear(2026);
        request.setCategoryName("餐饮");
        request.setAnnualLimit(new BigDecimal("18000"));
        request.setAlertThreshold(new BigDecimal("0.80"));

        personalBillService.createBudget(1L, request);
        verify(personalBudgetMapper).insert(any(PersonalBudget.class));
    }

    @Test
    void listBudgetsShouldDefaultCurrentYear() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        when(personalBudgetMapper.selectList(any())).thenReturn(List.of(
                buildBudget(2001L, LocalDate.now().getYear(), "餐饮", new BigDecimal("15000.00"), new BigDecimal("0.80"))
        ));

        List<AnnualBudgetVO> result = personalBillService.listBudgets(1L, new AnnualBudgetQueryRequest());

        assertEquals(1, result.size());
        assertEquals("餐饮", result.get(0).getCategoryName());
    }

    @Test
    void updateShouldRejectForeignBill() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        PersonalBill foreignBill = buildBill(1001L, "EXPENSE", "餐饮", new BigDecimal("88.00"), LocalDate.of(2026, 5, 10));
        foreignBill.setOwnerUserId(2L);
        when(personalBillMapper.selectOwnedForUpdate(1001L, 1L)).thenReturn(foreignBill);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> personalBillService.update(1L, 1001L, buildBillRequest()));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void linkedBillsRejectDirectUpdateAndDelete() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        PersonalBill linked = buildBill(1001L, "EXPENSE", "交通", new BigDecimal("100.00"), LocalDate.of(2026, 5, 10));
        linked.setSourceType("TRAVEL_RECORD");
        linked.setSourceId(2001L);
        when(personalBillMapper.selectOwnedForUpdate(1001L, 1L)).thenReturn(linked);
        assertThrows(BusinessException.class, () -> personalBillService.update(1L, 1001L, buildBillRequest()));
        assertThrows(BusinessException.class, () -> personalBillService.delete(1L, 1001L));
        verify(personalBillMapper, never()).updateById(any(PersonalBill.class));
        verify(personalBillMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void refundsReduceExpenseInsteadOfIncreasingIncome() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        PersonalBill payment = buildBill(1001L, "EXPENSE", "交通", new BigDecimal("500.00"), LocalDate.of(2026, 5, 10));
        PersonalBill refund = buildBill(1002L, "EXPENSE", "交通", new BigDecimal("-450.00"), LocalDate.of(2026, 5, 11));
        refund.setSourceType("TRAVEL_RECORD");
        refund.setSourceEntry("REFUND");
        when(personalBillMapper.selectList(any())).thenReturn(List.of(payment, refund));
        when(personalBudgetMapper.selectList(any())).thenReturn(List.of());
        PersonalBillSummaryQueryRequest request = new PersonalBillSummaryQueryRequest();
        request.setMonth("2026-05");
        PersonalBillSummaryVO summary = personalBillService.getSummary(1L, request);
        assertEquals(new BigDecimal("50.00"), summary.getCurrentMonthExpense());
        assertEquals(new BigDecimal("0.00"), summary.getCurrentMonthIncome());
        assertEquals(new BigDecimal("-50.00"), summary.getCurrentMonthBalance());
        assertEquals(new BigDecimal("50.00"), summary.getCategoryDistribution().get(0).getAmount());
    }

    @Test
    void foreignCurrencySummaryFiltersCurrencyAndExcludesCnyBudgets() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        when(personalBillMapper.selectList(any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?> wrapper = invocation.getArgument(0);
            org.junit.jupiter.api.Assertions.assertTrue(wrapper.getSqlSegment().contains("currency ="));
            org.junit.jupiter.api.Assertions.assertTrue(wrapper.getParamNameValuePairs().containsValue("USD"));
            PersonalBill usd = buildBill(1001L, "EXPENSE", "交通", new BigDecimal("20.00"), LocalDate.of(2026, 5, 10));
            usd.setCurrency("USD");
            return List.of(usd);
        });
        PersonalBillSummaryQueryRequest request = new PersonalBillSummaryQueryRequest();
        request.setMonth("2026-05");
        request.setCurrency("usd");
        PersonalBillSummaryVO summary = personalBillService.getSummary(1L, request);
        assertEquals(new BigDecimal("20.00"), summary.getCurrentMonthExpense());
        assertEquals(new BigDecimal("0.00"), summary.getAnnualBudgetAmount());
        assertEquals(0, summary.getBudgetProgressList().size());
        verify(personalBudgetMapper, never()).selectList(any());
    }

    @Test
    void linkedListFiltersOwnerCurrencyAndSource() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        when(personalBillMapper.selectList(any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?> wrapper = invocation.getArgument(0);
            String sql = wrapper.getSqlSegment();
            for (String column : List.of("owner_user_id", "currency", "source_type", "source_id")) {
                org.junit.jupiter.api.Assertions.assertTrue(sql.contains(column + " ="));
            }
            org.junit.jupiter.api.Assertions.assertTrue(wrapper.getParamNameValuePairs().containsValue("CNY"));
            org.junit.jupiter.api.Assertions.assertTrue(wrapper.getParamNameValuePairs().containsValue("TRAVEL_RECORD"));
            org.junit.jupiter.api.Assertions.assertTrue(wrapper.getParamNameValuePairs().containsValue(2001L));
            return List.of();
        });
        PersonalBillQueryRequest request = new PersonalBillQueryRequest();
        request.setTravelRecordId(2001L);
        personalBillService.page(1L, request);
    }

    @Test
    void detachedRefundCanBeEditedWithoutLosingExpenseDirection() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        PersonalBill refund = buildBill(1001L, "EXPENSE", "交通", new BigDecimal("-450.00"), LocalDate.of(2026, 5, 11));
        when(personalBillMapper.selectOwnedForUpdate(1001L, 1L)).thenReturn(refund);
        SavePersonalBillRequest request = buildBillRequest();
        request.setRefund(true);
        request.setAmount(new BigDecimal("440.00"));
        PersonalBillVO result = personalBillService.update(1L, 1001L, request);
        assertEquals("EXPENSE", result.getBillType());
        assertEquals(new BigDecimal("-440.00"), result.getAmount());
        verify(personalBillMapper).updateById(refund);
    }

    @Test
    void refundModeCannotCreateIncome() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        SavePersonalBillRequest request = buildBillRequest();
        request.setRefund(true);
        request.setBillType("INCOME");
        assertThrows(BusinessException.class, () -> personalBillService.create(1L, request));
        verify(personalBillMapper, never()).insert(any(PersonalBill.class));
    }

    @Test
    void invalidCurrencyCannotBeSaved() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        SavePersonalBillRequest request = buildBillRequest();
        request.setCurrency("NOT_A_CURRENCY");
        assertThrows(BusinessException.class, () -> personalBillService.create(1L, request));
        verify(personalBillMapper, never()).insert(any(PersonalBill.class));
    }

    private SavePersonalBillRequest buildBillRequest() {
        SavePersonalBillRequest request = new SavePersonalBillRequest();
        request.setBillType("EXPENSE");
        request.setCategoryName("餐饮");
        request.setAmount(new BigDecimal("88.00"));
        request.setPaymentMethod("支付宝");
        request.setBillDate(LocalDate.of(2026, 5, 10));
        return request;
    }

    private User buildUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setEnabled(true);
        user.setStatus("ENABLED");
        return user;
    }

    private PersonalBill buildBill(Long id, String billType, String categoryName, BigDecimal amount, LocalDate billDate) {
        PersonalBill bill = new PersonalBill();
        bill.setId(id);
        bill.setOwnerUserId(1L);
        bill.setBillType(billType);
        bill.setCategoryName(categoryName);
        bill.setAmount(amount);
        bill.setPaymentMethod("支付宝");
        bill.setAccountName("招商银行卡");
        bill.setMerchantName("测试商户");
        bill.setBillDate(billDate);
        bill.setUpdatedAt(LocalDateTime.now());
        return bill;
    }

    private PersonalBudget buildBudget(Long id, int year, String categoryName, BigDecimal annualLimit, BigDecimal alertThreshold) {
        PersonalBudget budget = new PersonalBudget();
        budget.setId(id);
        budget.setOwnerUserId(1L);
        budget.setBudgetYear(year);
        budget.setCategoryName(categoryName);
        budget.setAnnualLimit(annualLimit);
        budget.setAlertThreshold(alertThreshold);
        budget.setUpdatedAt(LocalDateTime.now());
        return budget;
    }
}
