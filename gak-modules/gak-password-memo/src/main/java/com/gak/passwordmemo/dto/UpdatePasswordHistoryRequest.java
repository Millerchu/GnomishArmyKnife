package com.gak.passwordmemo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 手工编辑历史密码请求，密码留空时保持原值。
 */
public class UpdatePasswordHistoryRequest {

    @Size(max = 128, message = "password 长度不能超过 128")
    private String password;

    @NotNull(message = "usageStartedAt 不能为空")
    private LocalDateTime usageStartedAt;

    @NotNull(message = "usageEndedAt 不能为空")
    private LocalDateTime usageEndedAt;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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
