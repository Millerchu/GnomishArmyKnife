package com.gak.permission.vo;

import java.util.List;

/**
 * 保存用户应用授权结果。
 */
public class UpdateUserAppPermissionVO {

    private Long userId;
    private List<String> grantedFeatureCodes;
    private Integer permissionCount;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public List<String> getGrantedFeatureCodes() {
        return grantedFeatureCodes;
    }

    public void setGrantedFeatureCodes(List<String> grantedFeatureCodes) {
        this.grantedFeatureCodes = grantedFeatureCodes;
    }

    public Integer getPermissionCount() {
        return permissionCount;
    }

    public void setPermissionCount(Integer permissionCount) {
        this.permissionCount = permissionCount;
    }
}
