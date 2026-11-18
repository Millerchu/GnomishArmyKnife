package com.gak.aisubscription.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import com.gak.aisubscription.enums.*;
/** 智订写入参数；不接受客户端用户归属。 */
public record SaveAiPaymentRequest(@NotNull Long accountId, Long subscriptionId, @NotNull AiPaymentType type, @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=6) BigDecimal amount, @NotBlank @Pattern(regexp="[A-Z]{3}") String currency, @NotNull LocalDate paymentDate, LocalDate coverageStart, LocalDate coverageEnd, Long originalPaymentId, @NotBlank @Size(max=64) String requestKey, @Size(max=1000) String note) {}
