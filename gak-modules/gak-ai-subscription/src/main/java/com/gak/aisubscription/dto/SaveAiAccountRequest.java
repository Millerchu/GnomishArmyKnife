package com.gak.aisubscription.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import com.gak.aisubscription.enums.*;
/** 智订写入参数；不接受客户端用户归属。 */
public record SaveAiAccountRequest(@NotBlank @Size(max=32) String toolCode, @NotBlank @Size(max=100) String alias, @Size(max=1000) String note, Long passwordMemoId) {}
