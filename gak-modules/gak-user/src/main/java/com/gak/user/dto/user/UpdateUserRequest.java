package com.gak.user.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 编辑用户请求。
 */
public class UpdateUserRequest {

    @NotBlank(message = "username 不能为空")
    @Size(max = 64, message = "username 长度不能超过 64")
    private String username;

    @Size(max = 100, message = "displayName 长度不能超过 100")
    private String displayName;

    @Size(max = 20, message = "phone 长度不能超过 20")
    private String phone;

    @Email(message = "email 格式不正确")
    @Size(max = 100, message = "email 长度不能超过 100")
    private String email;

    @Size(max = 20, message = "roleCode 长度不能超过 20")
    private String roleCode;

    @Size(max = 20, message = "status 长度不能超过 20")
    private String status;

    private Boolean enabled;

    @Size(max = 255, message = "remark 长度不能超过 255")
    private String remark;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
