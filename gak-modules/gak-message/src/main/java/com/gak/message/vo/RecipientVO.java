package com.gak.message.vo;
/** 消息查询响应。 */
public record RecipientVO(String userId, String username, String displayName, java.time.LocalDateTime receivedAt, java.time.LocalDateTime readAt) {}
