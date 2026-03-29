package com.gak.datamigration.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 可迁移资源清单。
 */
public class DataMigrationResourcesVO {

    private List<SystemResourceVO> systemResources = new ArrayList<>();
    private List<BusinessAppVO> businessApps = new ArrayList<>();

    public List<SystemResourceVO> getSystemResources() {
        return systemResources;
    }

    public void setSystemResources(List<SystemResourceVO> systemResources) {
        this.systemResources = systemResources;
    }

    public List<BusinessAppVO> getBusinessApps() {
        return businessApps;
    }

    public void setBusinessApps(List<BusinessAppVO> businessApps) {
        this.businessApps = businessApps;
    }

    /**
     * 系统资源项。
     */
    public static class SystemResourceVO {

        private String code;
        private String name;
        private String description;
        private Boolean attachmentSupported;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Boolean getAttachmentSupported() {
            return attachmentSupported;
        }

        public void setAttachmentSupported(Boolean attachmentSupported) {
            this.attachmentSupported = attachmentSupported;
        }
    }

    /**
     * 业务应用项。
     */
    public static class BusinessAppVO {

        private Long id;
        private String appCode;
        private String featureCode;
        private String code;
        private String name;
        private String route;
        private String category;
        private String securityLevel;
        private Boolean enabled;
        private String description;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getAppCode() {
            return appCode;
        }

        public void setAppCode(String appCode) {
            this.appCode = appCode;
        }

        public String getFeatureCode() {
            return featureCode;
        }

        public void setFeatureCode(String featureCode) {
            this.featureCode = featureCode;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRoute() {
            return route;
        }

        public void setRoute(String route) {
            this.route = route;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getSecurityLevel() {
            return securityLevel;
        }

        public void setSecurityLevel(String securityLevel) {
            this.securityLevel = securityLevel;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
