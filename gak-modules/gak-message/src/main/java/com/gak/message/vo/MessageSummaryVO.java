package com.gak.message.vo;
/** 消息查询响应。 */
public record MessageSummaryVO(long unreadCount, java.util.Map<String, Long> categoryCounts, java.util.List<InboxMessageVO> recent, String permissionRevision) {}
