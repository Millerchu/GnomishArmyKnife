package com.gak.passwordmemo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

/**
 * 历史密码展示项。
 */
public class PasswordMemoHistoryVO {

    private Long id;
    private String maskedPassword;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime usageStartedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime usageEndedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMaskedPassword() {
        return maskedPassword;
    }

    public void setMaskedPassword(String maskedPassword) {
        this.maskedPassword = maskedPassword;
    }

    public LocalDateTime getUsageStartedAt() {
        return usageStartedAt;
    }

    public void setUsageStartedAt(LocalDateTime usageStartedAt) {
        this.usageStartedAt = usageStartedAt;
    }

    public LocalDateTime getUsageEndedAt() {
        return usageEndedAt;
    }

    public void setUsageEndedAt(LocalDateTime usageEndedAt) {
        this.usageEndedAt = usageEndedAt;
    }
}
