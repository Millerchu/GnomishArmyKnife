package com.gak.permission.dto;

import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * 覆盖保存用户应用授权请求。
 */
public class UpdateUserAppPermissionRequest {

    private List<@Size(max = 64, message = "应用编码长度不能超过 64") String> grantedFeatureCodes = new ArrayList<>();

    @Size(max = 255, message = "remark 长度不能超过 255")
    private String remark;

    public List<String> getGrantedFeatureCodes() {
        return grantedFeatureCodes;
    }

    public void setGrantedFeatureCodes(List<String> grantedFeatureCodes) {
        this.grantedFeatureCodes = grantedFeatureCodes;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
