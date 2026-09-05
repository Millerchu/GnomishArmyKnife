package com.gak.message.dto;
import com.gak.framework.message.MessageCategory;
import jakarta.validation.constraints.*;
/** 数据库分页查询条件。 */
public class MessageQuery {
    @Min(1) @Max(1000000) private int pageNo = 1;
    @Min(1) @Max(100) private int pageSize = 20;
    private MessageCategory category;
    private Boolean unread;
    @Size(max = 80) private String keyword = "";
    public int getPageNo() { return pageNo; }
    public void setPageNo(int value) { pageNo = value; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int value) { pageSize = value; }
    public MessageCategory getCategory() { return category; }
    public void setCategory(MessageCategory value) { category = value; }
    public Boolean getUnread() { return unread; }
    public void setUnread(Boolean value) { unread = value; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String value) { keyword = value; }
    public long offset() { return (long) (pageNo - 1) * pageSize; }
}
