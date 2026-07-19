package com.gak.attachment.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 当前用户头像绑定请求，附件 ID 列表为空表示移除头像。
 */
public class SaveUserAvatarRequest {

    @Size(max = 1, message = "头像最多只能选择 1 张")
    private List<Long> attachmentIds;

    public List<Long> getAttachmentIds() {
        return attachmentIds;
    }

    public void setAttachmentIds(List<Long> attachmentIds) {
        this.attachmentIds = attachmentIds;
    }
}
