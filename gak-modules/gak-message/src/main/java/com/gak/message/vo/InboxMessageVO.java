package com.gak.message.vo;
/** 消息查询响应。 */
public record InboxMessageVO(Long id, Long messageId, String title, String body, String category, String priority, String source, String target, java.time.LocalDateTime receivedAt, java.time.LocalDateTime readAt) {}
