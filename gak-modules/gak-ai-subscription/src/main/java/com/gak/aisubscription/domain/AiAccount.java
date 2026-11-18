package com.gak.aisubscription.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
import java.math.BigDecimal;

/** 智订持久化记录；用户归属仅由服务端写入。 */
@TableName("gak_ai_account")
public class AiAccount {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long ownerUserId;
    private String toolCode;
    private String alias;
    private String note;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long passwordMemoId;
    private Boolean archived;
    private LocalDateTime recordedAt;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getToolCode() { return toolCode; }
    public void setToolCode(String toolCode) { this.toolCode = toolCode; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getPasswordMemoId() { return passwordMemoId; }
    public void setPasswordMemoId(Long passwordMemoId) { this.passwordMemoId = passwordMemoId; }
    public Boolean getArchived() { return archived; }
    public void setArchived(Boolean archived) { this.archived = archived; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
