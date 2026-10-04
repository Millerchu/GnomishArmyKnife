package com.gak.travelrecord.dto;

import jakarta.validation.constraints.*;

/** 默认查询全部历史，数据库分页。 */
public class TravelRecordQueryRequest {
    @Min(1) @Max(1000000) private int pageNo = 1;
    @Min(1) @Max(100) private int pageSize = 20;
    @Min(1) @Max(9999) private Integer year;
    @Size(max = 20) private String transportType;
    @Size(max = 20) private String status;
    @Size(max = 96) private String keyword;
    private Long groupId;
    public int getPageNo() { return pageNo; }
    public void setPageNo(int value) { pageNo = value; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int value) { pageSize = value; }
    public Integer getYear() { return year; }
    public void setYear(Integer value) { year = value; }
    public String getTransportType() { return transportType; }
    public void setTransportType(String value) { transportType = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String value) { keyword = value; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long value) { groupId = value; }
}
