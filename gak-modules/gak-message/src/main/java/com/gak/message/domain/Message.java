package com.gak.message.domain;
import java.time.LocalDateTime;
/** 消息主表，仅映射持久化字段。 */
public record Message(Long id, String source, String idempotencyKey, String payloadHash,
        Long senderId, String audience, String category, String priority, String title,
        String body, String target, LocalDateTime createdAt) {}
