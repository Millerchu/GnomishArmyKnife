package com.gak.permission.vo;

import java.util.List;

/**
 * 用户应用授权结果。
 */
public class UserAppPermissionVO {

    private Long userId;
    private List<String> grantedFeatureCodes;
    private List<AppCatalogVO> apps;
    private String permissionSource;

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

    public List<AppCatalogVO> getApps() {
        return apps;
    }

    public void setApps(List<AppCatalogVO> apps) {
        this.apps = apps;
    }

    public String getPermissionSource() {
        return permissionSource;
    }

    public void setPermissionSource(String permissionSource) {
        this.permissionSource = permissionSource;
    }
}
