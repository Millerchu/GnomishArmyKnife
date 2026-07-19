package com.gak.passwordmemo.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 密码备忘录历史密码实体。
 */
@TableName("gak_password_memo_history")
public class PasswordMemoHistory {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long memoId;
    private Long ownerUserId;
    private String passwordCiphertext;
    private String passwordNonce;
    private LocalDateTime usageStartedAt;
    private LocalDateTime usageEndedAt;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMemoId() {
        return memoId;
    }

    public void setMemoId(Long memoId) {
        this.memoId = memoId;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getPasswordCiphertext() {
        return passwordCiphertext;
    }

    public void setPasswordCiphertext(String passwordCiphertext) {
        this.passwordCiphertext = passwordCiphertext;
    }

    public String getPasswordNonce() {
        return passwordNonce;
    }

    public void setPasswordNonce(String passwordNonce) {
        this.passwordNonce = passwordNonce;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
