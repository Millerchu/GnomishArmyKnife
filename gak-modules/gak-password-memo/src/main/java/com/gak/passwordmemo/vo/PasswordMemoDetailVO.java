package com.gak.passwordmemo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 密码备忘录详情。
 */
public class PasswordMemoDetailVO {

    private Long id;
    private String category;
    private String siteName;
    private String siteUrl;
    private String username;
    private String registeredPhone;
    private String registeredEmail;
    private String remark;
    private String maskedPassword;
    private List<PasswordMemoHistoryVO> passwordHistory;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getSiteUrl() {
        return siteUrl;
    }

    public void setSiteUrl(String siteUrl) {
        this.siteUrl = siteUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRegisteredPhone() {
        return registeredPhone;
    }

    public void setRegisteredPhone(String registeredPhone) {
        this.registeredPhone = registeredPhone;
    }

    public String getRegisteredEmail() {
        return registeredEmail;
    }

    public void setRegisteredEmail(String registeredEmail) {
        this.registeredEmail = registeredEmail;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getMaskedPassword() {
        return maskedPassword;
    }

    public void setMaskedPassword(String maskedPassword) {
        this.maskedPassword = maskedPassword;
    }

    public List<PasswordMemoHistoryVO> getPasswordHistory() {
        return passwordHistory;
    }

    public void setPasswordHistory(List<PasswordMemoHistoryVO> passwordHistory) {
        this.passwordHistory = passwordHistory;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
