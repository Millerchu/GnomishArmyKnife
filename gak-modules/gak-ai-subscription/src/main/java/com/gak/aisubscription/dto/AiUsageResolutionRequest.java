package com.gak.aisubscription.dto;
import com.gak.aisubscription.enums.AiUsageResolution;
import jakarta.validation.constraints.NotNull;
/** 冲突处理不覆盖原始观测。 */
public record AiUsageResolutionRequest(@NotNull AiUsageResolution action) {}
