package com.gak.personalbills.vo;

import com.gak.personalbills.domain.BankCardType;

/** 银行卡管理和账单选卡共用的卡片资料。 */
public record PersonalBankCardVO(Long id, BankCardType cardType, String cardName, String institution,
                                 String lastFour, Long creditAccountId, String note, boolean archived) {
}
