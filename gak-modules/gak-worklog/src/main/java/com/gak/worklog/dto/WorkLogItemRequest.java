package com.gak.worklog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 工作日志内容条目入参。
 */
public class WorkLogItemRequest {

    @NotBlank
    @Size(max = 4000)
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Digits(integer = 1, fraction = 1)
    private BigDecimal personDay;
    private String content;

    @Size(max = 16)
    private String status;

    @Size(max = 255)
    private String zentaoNo;

    public BigDecimal getPersonDay() {
        return personDay;
    }

    public void setPersonDay(BigDecimal personDay) {
        this.personDay = personDay;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getZentaoNo() {
        return zentaoNo;
    }

    public void setZentaoNo(String zentaoNo) {
        this.zentaoNo = zentaoNo;
    }
}
