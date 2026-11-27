package com.gak.healthrecord.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

/**
 * 健康指标分页查询参数。
 */
public class HealthRecordQueryRequest {

    @Min(value = 1, message = "pageNo 不能小于 1")
    private Integer pageNo = 1;

    @Min(value = 1, message = "pageSize 不能小于 1")
    @Max(value = 500, message = "pageSize 不能大于 500")
    private Integer pageSize = 200;

    private String metricKey;

    private LocalDate measureDate;

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getMetricKey() {
        return metricKey;
    }

    public void setMetricKey(String metricKey) {
        this.metricKey = metricKey;
    }

    public LocalDate getMeasureDate() {
        return measureDate;
    }

    public void setMeasureDate(LocalDate measureDate) {
        this.measureDate = measureDate;
    }
}
