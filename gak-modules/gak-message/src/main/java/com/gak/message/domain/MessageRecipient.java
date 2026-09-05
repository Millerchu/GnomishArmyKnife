package com.gak.message.domain;
import java.time.LocalDateTime;
/** 用户收件记录，已读时间为空表示未读。 */
public record MessageRecipient(Long id, Long messageId, Long userId, LocalDateTime receivedAt,
        LocalDateTime readAt) {}
