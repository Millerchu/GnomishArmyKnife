package com.gak.systemlog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 系统异常日志分页筛选。关键字只匹配已落库的脱敏摘要。
 */
public class SystemExceptionLogQuery {

    @Min(value = 1, message = "页码必须大于 0")
    @Max(value = 1000000, message = "页码超出范围")
    private int pageNo = 1;

    @Min(value = 1, message = "每页条数须在 1 到 100 之间")
    @Max(value = 100, message = "每页条数须在 1 到 100 之间")
    private int pageSize = 20;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime occurredFrom;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime occurredTo;

    @Size(max = 255, message = "异常类型长度不能超过 255")
    private String exceptionType;

    @Size(max = 64, message = "追踪编号长度不能超过 64")
    private String traceId;

    @Size(max = 80, message = "关键字长度不能超过 80")
    private String keyword;

    public int getPageNo() {
        return pageNo;
    }

    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public LocalDateTime getOccurredFrom() {
        return occurredFrom;
    }

    public void setOccurredFrom(LocalDateTime occurredFrom) {
        this.occurredFrom = occurredFrom;
    }

    public LocalDateTime getOccurredTo() {
        return occurredTo;
    }

    public void setOccurredTo(LocalDateTime occurredTo) {
        this.occurredTo = occurredTo;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
