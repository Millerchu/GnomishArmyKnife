package com.gak.message.mapper;

import com.gak.framework.message.*;
import com.gak.framework.response.PagedResult;
import com.gak.message.domain.Message;
import com.gak.message.dto.MessageQuery;
import com.gak.message.vo.*;
import java.util.*;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

/** 消息持久化；所有收件操作都在 SQL 中绑定当前用户。 */
@Repository
public class MessageMapper {
    private static final String INBOX_COLUMNS = "r.id, m.id message_id, m.title, m.body, m.category, m.priority, m.source, m.target, r.received_at, r.read_at";
    private static final String INBOX_JOIN = " FROM gak_message_recipient r JOIN gak_message m ON m.id=r.message_id ";
    private static final String INBOX_FILTER = " WHERE r.user_id=:userId AND (:category IS NULL OR m.category=:category) AND (:unread IS NULL OR (r.read_at IS NULL)=:unread) ";
    private static final String VALID_USER = "enabled=TRUE AND status='ENABLED'";
    private static final int INSERT_BATCH_SIZE = 500;
    private final NamedParameterJdbcTemplate jdbc;
    public MessageMapper(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 事务级锁使并发幂等请求串行，避免唯一键异常使调用方事务失效。 */
    public void lockKey(String source, String key) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))", Map.of("key", source + ":" + key), rs -> {});
    }
    public Optional<Message> findByKey(String source, String key) {
        return jdbc.query("SELECT * FROM gak_message WHERE source=:source AND idempotency_key=:key",
                Map.of("source", source, "key", key), new DataClassRowMapper<>(Message.class)).stream().findFirst();
    }
    public List<Long> resolveRecipients(MessageAudience audience, List<Long> ids) {
        String filter = audience == MessageAudience.ALL ? "" : " AND id IN (:ids)";
        return jdbc.queryForList("SELECT id FROM gak_user WHERE " + VALID_USER + filter + " ORDER BY id", Map.of("ids", ids), Long.class);
    }
    public Long insert(PublishMessageCommand command, String hash) {
        MapSqlParameterSource args = new MapSqlParameterSource()
                .addValue("source", command.source()).addValue("key", command.idempotencyKey())
                .addValue("hash", hash).addValue("sender", command.senderId())
                .addValue("audience", command.audience().name()).addValue("category", command.category().name())
                .addValue("priority", command.priority().name()).addValue("title", command.title())
                .addValue("body", command.body()).addValue("target", command.target() == null ? null : command.target().name());
        return jdbc.queryForObject("""
                INSERT INTO gak_message(source,idempotency_key,payload_hash,sender_id,audience,category,priority,title,body,target)
                VALUES(:source,:key,:hash,:sender,:audience,:category,:priority,:title,:body,:target) RETURNING id
                """, args, Long.class);
    }
    public void insertRecipients(Long messageId, List<Long> users) {
        for (int offset = 0; offset < users.size(); offset += INSERT_BATCH_SIZE) {
            SqlParameterSource[] batch = users.subList(offset, Math.min(offset + INSERT_BATCH_SIZE, users.size())).stream()
                    .map(id -> new MapSqlParameterSource("messageId", messageId).addValue("userId", id)).toArray(SqlParameterSource[]::new);
            jdbc.batchUpdate("INSERT INTO gak_message_recipient(message_id,user_id) VALUES(:messageId,:userId)", batch);
        }
    }
    public PagedResult<InboxMessageVO> inbox(Long userId, MessageQuery query) {
        MapSqlParameterSource args = queryArgs(query).addValue("userId", userId);
        Long total = jdbc.queryForObject("SELECT count(*)" + INBOX_JOIN + INBOX_FILTER, args, Long.class);
        List<InboxMessageVO> list = jdbc.query("SELECT " + INBOX_COLUMNS + INBOX_JOIN + INBOX_FILTER
                + "ORDER BY r.received_at DESC,r.id DESC LIMIT :limit OFFSET :offset", args, new DataClassRowMapper<>(InboxMessageVO.class));
        return new PagedResult<>(list, total == null ? 0 : total);
    }
    public Optional<InboxMessageVO> detail(Long userId, Long id) {
        return jdbc.query("SELECT " + INBOX_COLUMNS + INBOX_JOIN + "WHERE r.user_id=:userId AND r.id=:id",
                Map.of("userId", userId, "id", id), new DataClassRowMapper<>(InboxMessageVO.class)).stream().findFirst();
    }
    public Map<String, Long> unreadCounts(Long userId) {
        return jdbc.query("SELECT m.category,count(*) amount" + INBOX_JOIN
                + "WHERE r.user_id=:userId AND r.read_at IS NULL GROUP BY m.category", Map.of("userId", userId), rs -> {
            Map<String, Long> counts = new LinkedHashMap<>();
            for (MessageCategory category : MessageCategory.values()) { counts.put(category.name(), 0L); }
            while (rs.next()) { counts.put(rs.getString("category"), rs.getLong("amount")); }
            return counts;
        });
    }
    /** 审计总数用于检测权限变化，不以最大 ID 假设事务提交顺序。 */
    public String permissionRevision(Long userId) {
        return jdbc.queryForObject("SELECT count(*)::text FROM gak_permission_audit_log WHERE target_user_id=:userId AND action_type='REPLACE_APPS'", Map.of("userId", userId), String.class);
    }
    public int markRead(Long userId, Long id) {
        return jdbc.update("UPDATE gak_message_recipient SET read_at=clock_timestamp() WHERE user_id=:userId AND id=:id AND read_at IS NULL", Map.of("userId", userId, "id", id));
    }
    /** 单条 UPDATE 使用语句快照，之后提交的新消息不会被意外标记。 */
    public int markAllRead(Long userId) {
        return jdbc.update("UPDATE gak_message_recipient SET read_at=clock_timestamp() WHERE user_id=:userId AND read_at IS NULL", Map.of("userId", userId));
    }
    public PagedResult<SentMessageVO> sent(MessageQuery query) {
        MapSqlParameterSource args = queryArgs(query);
        String filter = " WHERE (:category IS NULL OR m.category=:category) ";
        Long total = jdbc.queryForObject("SELECT count(*) FROM gak_message m" + filter, args, Long.class);
        List<SentMessageVO> list = jdbc.query("""
                SELECT m.id,m.title,m.body,m.category,m.priority,m.source,m.audience,m.created_at,
                  (SELECT count(*) FROM gak_message_recipient r WHERE r.message_id=m.id) recipient_count,
                  (SELECT count(*) FROM gak_message_recipient r WHERE r.message_id=m.id AND r.read_at IS NOT NULL) read_count
                FROM gak_message m
                """ + filter + "ORDER BY m.created_at DESC,m.id DESC LIMIT :limit OFFSET :offset", args, new DataClassRowMapper<>(SentMessageVO.class));
        return new PagedResult<>(list, total == null ? 0 : total);
    }
    public PagedResult<RecipientVO> recipients(Long messageId, MessageQuery query) {
        MapSqlParameterSource args = queryArgs(query).addValue("messageId", messageId);
        Long total = jdbc.queryForObject("SELECT count(*) FROM gak_message_recipient WHERE message_id=:messageId", args, Long.class);
        List<RecipientVO> list = jdbc.query("""
                SELECT r.user_id::text user_id,coalesce(u.username,'已删除用户') username,
                  coalesce(u.display_name,'已删除用户') display_name,r.received_at,r.read_at
                FROM gak_message_recipient r LEFT JOIN gak_user u ON u.id=r.user_id
                WHERE r.message_id=:messageId ORDER BY r.id DESC LIMIT :limit OFFSET :offset
                """, args, new DataClassRowMapper<>(RecipientVO.class));
        return new PagedResult<>(list, total == null ? 0 : total);
    }
    public PagedResult<RecipientOptionVO> recipientOptions(MessageQuery query) {
        MapSqlParameterSource args = queryArgs(query).addValue("keyword", "%" + Objects.toString(query.getKeyword(), "") + "%");
        String filter = " FROM gak_user WHERE " + VALID_USER + " AND (username ILIKE :keyword OR display_name ILIKE :keyword) ";
        Long total = jdbc.queryForObject("SELECT count(*)" + filter, args, Long.class);
        List<RecipientOptionVO> list = jdbc.query("SELECT id::text id,username,display_name" + filter
                + "ORDER BY username,id LIMIT :limit OFFSET :offset", args, new DataClassRowMapper<>(RecipientOptionVO.class));
        return new PagedResult<>(list, total == null ? 0 : total);
    }
    private MapSqlParameterSource queryArgs(MessageQuery query) {
        return new MapSqlParameterSource().addValue("category", query.getCategory() == null ? null : query.getCategory().name(), java.sql.Types.VARCHAR)
                .addValue("unread", query.getUnread(), java.sql.Types.BOOLEAN).addValue("limit", query.getPageSize()).addValue("offset", query.offset());
    }
}
