package com.gak.personalbills.service;

import com.gak.personalbills.domain.PersonalCreditAccount;
import com.gak.personalbills.mapper.PersonalCreditAccountMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** 授信统计边界与用户隔离回归。 */
class PersonalCreditServiceTest {
    private final PersonalCreditAccountMapper mapper = mock(PersonalCreditAccountMapper.class);
    private final PersonalCreditService service = new PersonalCreditService(mapper);

    @Test
    void summaryShouldAggregateAndClampEachAccountAvailability() {
        PersonalCreditAccount first = account("100.00", "120.00");
        PersonalCreditAccount second = account("200.00", "50.00");
        when(mapper.selectList(any())).thenReturn(List.of(first, second));
        var result = service.summary(1L);
        assertEquals(new BigDecimal("300.00"), result.getTotalLimit());
        assertEquals(new BigDecimal("170.00"), result.getTotalUsed());
        assertEquals(new BigDecimal("150.00"), result.getTotalAvailable());
        assertEquals(new BigDecimal("0.5667"), result.getUsageRate());
    }

    @Test
    void emptySummaryShouldAvoidDivisionByZero() {
        when(mapper.selectList(any())).thenReturn(List.of());
        assertEquals(new BigDecimal("0.0000"), service.summary(1L).getUsageRate());
    }

    @Test
    void foreignAccountCannotBeDeletedOrUpdated() {
        PersonalCreditAccount foreign = account("100.00", "0.00");
        foreign.setOwnerUserId(2L);
        when(mapper.selectById(3L)).thenReturn(foreign);
        assertThrows(ResponseStatusException.class, () -> service.delete(1L, 3L));
        assertThrows(ResponseStatusException.class, () -> service.save(1L, 3L, null));
    }

    private PersonalCreditAccount account(String limit, String used) {
        PersonalCreditAccount account = new PersonalCreditAccount();
        account.setCreditLimit(new BigDecimal(limit));
        account.setUsedAmount(new BigDecimal(used));
        return account;
    }
}
