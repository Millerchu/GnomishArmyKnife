package com.gak.message.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.framework.message.*;
import com.gak.framework.response.PagedResult;
import com.gak.message.domain.Message;
import com.gak.message.dto.*;
import com.gak.message.mapper.MessageMapper;
import com.gak.message.vo.*;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import com.gak.user.constant.UserSecurityConstants;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 消息业务与事务边界；入库失败回滚，推送失败不改变发送结果。 */
@Service
public class MessageService implements MessagePublisher {
    private static final Logger LOG = LoggerFactory.getLogger(MessageService.class);
    private static final String ADMIN_SOURCE_PREFIX = "ADMIN:";
    private static final String EVENT_CREATED = "message-created";
    private static final String EVENT_READ = "read-changed";
    private static final int RECENT_LIMIT = 10;
    private final MessageMapper mapper;
    private final UserMapper users;
    private final MessageStreamService streams;
    private final Validator validator;
    private final ObjectMapper json;

    public MessageService(MessageMapper mapper, UserMapper users, MessageStreamService streams, Validator validator, ObjectMapper json) {
        this.mapper = mapper; this.users = users; this.streams = streams; this.validator = validator; this.json = json;
    }

    @Override
    @Transactional
    public Long publish(PublishMessageCommand command) {
        validate(command);
        List<Long> requested = command.userIds().stream().distinct().sorted().toList();
        PublishMessageCommand normalized = new PublishMessageCommand(command.source(), command.idempotencyKey(), command.senderId(),
                command.audience(), requested, command.category(), command.priority(), command.title(), command.body(), command.target());
        String hash = fingerprint(normalized);
        mapper.lockKey(command.source(), command.idempotencyKey());
        Optional<Message> existing = mapper.findByKey(command.source(), command.idempotencyKey());
        if (existing.isPresent()) {
            if (!existing.get().payloadHash().equals(hash)) { throw failure(HttpStatus.CONFLICT, "该发送标识已用于不同内容，请重新预览后发送"); }
            return existing.get().id();
        }
        List<Long> recipients = mapper.resolveRecipients(command.audience(), requested);
        if (recipients.isEmpty() || (command.audience() == MessageAudience.USERS && recipients.size() != requested.size())) {
            throw failure(HttpStatus.BAD_REQUEST, "接收用户不存在、已停用或接收范围为空");
        }
        Long id = mapper.insert(normalized, hash);
        mapper.insertRecipients(id, recipients);
        afterCommit(recipients, EVENT_CREATED, id);
        return id;
    }

    @Transactional
    public Long send(Long userId, SendMessageRequest request) {
        requireAdmin(userId);
        return publish(new PublishMessageCommand(ADMIN_SOURCE_PREFIX + userId, request.idempotencyKey(), userId, request.audience(),
                request.userIds(), request.category(), request.priority(), request.title(), request.body(), null));
    }

    public PagedResult<InboxMessageVO> inbox(Long userId, MessageQuery query) {
        requireUser(userId);
        return mapper.inbox(userId, query);
    }

    public InboxMessageVO detail(Long userId, Long id) {
        requireUser(userId);
        return mapper.detail(userId, id).orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "消息不存在或无权访问"));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MessageSummaryVO summary(Long userId) {
        requireUser(userId);
        long started = System.nanoTime();
        Map<String, Long> counts = mapper.unreadCounts(userId);
        MessageQuery query = new MessageQuery();
        query.setPageSize(RECENT_LIMIT);
        MessageSummaryVO result = new MessageSummaryVO(counts.values().stream().mapToLong(Long::longValue).sum(), counts,
                mapper.inbox(userId, query).list(), mapper.permissionRevision(userId));
        LOG.debug("消息摘要查询耗时={}ms", (System.nanoTime() - started) / 1_000_000);
        return result;
    }

    @Transactional
    public void read(Long userId, Long id) {
        detail(userId, id);
        if (mapper.markRead(userId, id) > 0) { afterCommit(List.of(userId), EVENT_READ, null); }
    }

    @Transactional
    public void readAll(Long userId) {
        requireUser(userId);
        if (mapper.markAllRead(userId) > 0) { afterCommit(List.of(userId), EVENT_READ, null); }
    }

    public SseEmitter connect(Long userId) { requireUser(userId); return streams.connect(userId); }
    public PagedResult<SentMessageVO> sent(Long userId, MessageQuery query) { requireAdmin(userId); return mapper.sent(query); }
    public PagedResult<RecipientVO> recipients(Long userId, Long id, MessageQuery query) { requireAdmin(userId); return mapper.recipients(id, query); }
    public PagedResult<RecipientOptionVO> recipientOptions(Long userId, MessageQuery query) { requireAdmin(userId); return mapper.recipientOptions(query); }

    private User requireUser(Long userId) {
        User user = users.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled()) || !UserSecurityConstants.ENABLED_STATUS.equals(user.getStatus())) {
            throw failure(HttpStatus.UNAUTHORIZED, "用户已失效，请重新登录");
        }
        return user;
    }

    private void requireAdmin(Long userId) {
        if (!UserSecurityConstants.ADMIN_ROLE_CODE.equalsIgnoreCase(requireUser(userId).getRoleCode())) {
            throw failure(HttpStatus.FORBIDDEN, "只有管理员可以管理消息");
        }
    }

    private void validate(PublishMessageCommand command) {
        if (command == null || !validator.validate(command).isEmpty()) { throw failure(HttpStatus.BAD_REQUEST, "消息参数不完整或超出长度限制"); }
        if (command.audience() == MessageAudience.USERS && command.userIds().isEmpty()) { throw failure(HttpStatus.BAD_REQUEST, "请选择接收用户"); }
        if (command.audience() == MessageAudience.ALL && !command.userIds().isEmpty()) { throw failure(HttpStatus.BAD_REQUEST, "全体发送不可同时指定用户"); }
    }

    private String fingerprint(PublishMessageCommand command) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(command).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException("生成消息幂等摘要失败", exception);
        }
    }

    private void afterCommit(List<Long> recipients, String event, Long messageId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { streams.notifyUsers(recipients, event, messageId); }
                catch (RuntimeException exception) { LOG.warn("消息实时通知失败，客户端将通过补查恢复，messageId={}", messageId, exception); }
            }
        });
    }

    private ResponseStatusException failure(HttpStatus status, String reason) { return new ResponseStatusException(status, reason); }
}
