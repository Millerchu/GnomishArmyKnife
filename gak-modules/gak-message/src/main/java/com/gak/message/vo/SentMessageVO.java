package com.gak.message.vo;
/** 消息查询响应。 */
public record SentMessageVO(Long id, String title, String body, String category, String priority, String source, String audience, java.time.LocalDateTime createdAt, long recipientCount, long readCount) {}
