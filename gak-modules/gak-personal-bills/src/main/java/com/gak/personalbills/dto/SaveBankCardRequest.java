package com.gak.personalbills.dto;

import com.gak.personalbills.domain.BankCardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 新增或编辑银行卡。 */
public record SaveBankCardRequest(
        @NotNull(message = "请选择卡片类型") BankCardType cardType,
        @NotBlank(message = "请填写卡片名称") @Size(max = 56) String cardName,
        @NotBlank(message = "请填写银行") @Size(max = 64) String institution,
        @Pattern(regexp = "[0-9]{4}", message = "尾号须为四位数字") String lastFour,
        @Positive Long creditAccountId,
        @Size(max = 160) String note) {
}
