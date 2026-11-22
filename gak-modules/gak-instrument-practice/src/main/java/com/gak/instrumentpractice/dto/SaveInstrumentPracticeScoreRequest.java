package com.gak.instrumentpractice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 保存单声部简谱及演奏设置的请求。
 */
public record SaveInstrumentPracticeScoreRequest(
        @NotBlank(message = "请输入曲名")
        @Size(max = 80, message = "曲名最多 80 个字符")
        String title,
        @NotBlank(message = "请输入简谱")
        @Size(max = 5000, message = "简谱最多 5000 个字符")
        String notation,
        @NotNull @Min(0) @Max(11) Integer tonic,
        @NotNull @Pattern(regexp = "major|natural-minor") String mode,
        @NotNull @Pattern(regexp = "2/4|3/4|4/4|6/8") String meter,
        @NotNull @Min(40) @Max(220) Integer bpm,
        @Size(max = 24) String instrumentId,
        @Size(max = 64) String tuningId
) {
}
