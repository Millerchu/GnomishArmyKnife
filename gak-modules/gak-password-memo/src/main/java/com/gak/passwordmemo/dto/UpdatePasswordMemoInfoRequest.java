package com.gak.passwordmemo.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

/**
 * 编辑密码备忘录基础信息请求，不允许携带密码。
 */
public class UpdatePasswordMemoInfoRequest {

    @NotBlank(message = "category 不能为空")
    @Size(max = 64, message = "category 长度不能超过 64")
    private String category;

    @NotBlank(message = "siteName 不能为空")
    @Size(max = 64, message = "siteName 长度不能超过 64")
    private String siteName;

    @NotBlank(message = "siteUrl 不能为空")
    @Size(max = 255, message = "siteUrl 长度不能超过 255")
    private String siteUrl;

    @Size(max = 100, message = "username 长度不能超过 100")
    private String username;

    @Size(max = 20, message = "registeredPhone 长度不能超过 20")
    private String registeredPhone;

    @Email(message = "registeredEmail 格式不正确")
    @Size(max = 100, message = "registeredEmail 长度不能超过 100")
    private String registeredEmail;

    @Size(max = 255, message = "remark 长度不能超过 255")
    private String remark;

    @AssertTrue(message = "username、registeredPhone、registeredEmail 至少填写一项")
    public boolean isAccountIdentityProvided() {
        return StringUtils.hasText(username)
                || StringUtils.hasText(registeredPhone)
                || StringUtils.hasText(registeredEmail);
    }

    public String getSiteName() {
        return siteName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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
}
