package com.gak.datamigration.dto;

import com.gak.personalbills.domain.PersonalBankCard;
import com.gak.personalbills.domain.PersonalBill;
import com.gak.personalbills.domain.PersonalBudget;
import com.gak.personalbills.domain.PersonalCreditAccount;
import java.util.List;

/** 个人账单、预算及资金账户的迁移载荷。 */
public record PersonalBillsMigrationPayload(List<PersonalBill> bills, List<PersonalBudget> budgets,
                                            List<PersonalCreditAccount> creditAccounts,
                                            List<PersonalBankCard> bankCards) {
}
