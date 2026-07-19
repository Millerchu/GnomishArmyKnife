package com.gak.passwordmemo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 手工新增历史密码请求。
 */
public class CreatePasswordHistoryRequest {

    @NotBlank(message = "password 不能为空")
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
