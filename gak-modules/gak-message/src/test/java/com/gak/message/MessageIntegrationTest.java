package com.gak.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.framework.message.*;
import com.gak.message.dto.*;
import com.gak.message.mapper.MessageMapper;
import com.gak.message.service.*;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import javax.sql.DataSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import com.gak.message.controller.MessageController;
import com.gak.user.service.user.TokenService;
import com.gak.framework.exception.GlobalExceptionHandler;
import java.net.URI;
import java.net.http.*;
import java.io.*;
import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实 PostgreSQL 隔离 schema 验证，不读取或修改现有业务表。 */
@EnabledIfEnvironmentVariable(named = "GAK_MESSAGE_TEST_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageIntegrationTest {
    private static final String SCHEMA = "gak_message_test_" + UUID.randomUUID().toString().replace("-", "");
    private AnnotationConfigServletWebServerApplicationContext context;
    private JdbcTemplate jdbc;
    private MessageService service;
    private MessageStreamService streams;
    private UserMapper users;
    private TransactionTemplate transaction;

    @Configuration
    @EnableWebMvc
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({MessageService.class, MessageMapper.class, MessageController.class, TokenService.class, GlobalExceptionHandler.class, com.gak.message.controller.MessageExceptionHandler.class})
    static class Config {
        @Bean org.springframework.boot.web.servlet.ServletRegistrationBean<org.springframework.web.servlet.DispatcherServlet> dispatcher(org.springframework.web.context.WebApplicationContext context) {
            var registration = new org.springframework.boot.web.servlet.ServletRegistrationBean<>(new org.springframework.web.servlet.DispatcherServlet(context), "/");
            registration.setAsyncSupported(true);
            return registration;
        }
        @Bean ServletWebServerFactory webServer() { return new TomcatServletWebServerFactory(0); }
        @Bean DataSource dataSource() {
            String url = System.getenv("GAK_MESSAGE_TEST_URL");
            DriverManagerDataSource source = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
            source.setUsername(System.getenv().getOrDefault("GAK_MESSAGE_TEST_USER", System.getProperty("user.name")));
            source.setPassword(System.getenv().getOrDefault("GAK_MESSAGE_TEST_PASSWORD", ""));
            return source;
        }
        @Bean NamedParameterJdbcTemplate jdbc(DataSource source) { return new NamedParameterJdbcTemplate(source); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean UserMapper users() { return mock(UserMapper.class); }
        @Bean MessageStreamService streams() { return spy(new MessageStreamService()); }
        @Bean Validator validator() { return Validation.buildDefaultValidatorFactory().getValidator(); }
        @Bean ObjectMapper json() { return new ObjectMapper(); }
    }

    @BeforeAll void setupSchema() throws Exception {
        context = new AnnotationConfigServletWebServerApplicationContext(Config.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.execute("CREATE SCHEMA " + SCHEMA);
        jdbc.execute("CREATE TABLE gak_user(id BIGINT PRIMARY KEY,username VARCHAR(60),display_name VARCHAR(60),enabled BOOLEAN,status VARCHAR(20))");
        jdbc.execute("CREATE TABLE gak_permission_audit_log(id BIGINT, target_user_id BIGINT,action_type VARCHAR(40))");
        String schema = Files.readString(Path.of("../../gak-start/src/main/resources/schema.sql"));
        String messageSchema = schema.substring(schema.indexOf("-- 站内消息先持久化"));
        jdbc.execute(messageSchema);
        jdbc.execute(messageSchema);
        service = context.getBean(MessageService.class);
        streams = context.getBean(MessageStreamService.class);
        users = context.getBean(UserMapper.class);
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    @AfterAll void cleanup() {
        if (jdbc != null) jdbc.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
        if (context != null) context.close();
    }

    @BeforeEach void resetRecords() {
        jdbc.execute("TRUNCATE gak_message_recipient,gak_message,gak_user,gak_permission_audit_log RESTART IDENTITY");
        jdbc.execute("INSERT INTO gak_user VALUES (1,'admin','管理员',true,'ENABLED'),(2,'alice','甲',true,'ENABLED'),(3,'bob','乙',true,'ENABLED'),(4,'disabled','停用',false,'DISABLED')");
        reset(streams, users);
        for (long id = 1; id <= 4; id++) {
            User user = new User(); user.setId(id); user.setRoleCode(id == 1 ? "ADMIN" : "USER");
            user.setEnabled(id != 4); user.setStatus(id == 4 ? "DISABLED" : "ENABLED");
            when(users.selectById(id)).thenReturn(user);
        }
    }

    private PublishMessageCommand command(String key, List<Long> recipients) {
        return new PublishMessageCommand("TEST", key, 1L, MessageAudience.USERS, recipients,
                MessageCategory.BUSINESS, MessagePriority.NORMAL, "测试标题", "<script>纯文本</script>\n第二行", null);
    }

    @Test void offlineInboxOwnershipReadAndStatistics() {
        Long messageId = service.publish(command("offline", List.of(2L, 2L)));
        assertEquals(1, service.summary(2L).unreadCount());
        assertEquals(0, service.summary(3L).unreadCount());
        var item = service.inbox(2L, new MessageQuery()).list().getFirst();
        assertEquals(messageId, item.messageId());
        assertNull(service.detail(2L, item.id()).readAt());
        assertThrows(ResponseStatusException.class, () -> service.read(3L, item.id()));
        assertThrows(ResponseStatusException.class, () -> service.detail(3L, item.id()));
        service.read(2L, item.id()); service.read(2L, item.id());
        assertEquals(0, service.summary(2L).unreadCount());
        assertEquals(1, service.sent(1L, new MessageQuery()).list().getFirst().readCount());
        assertEquals(1, service.recipients(1L, messageId, new MessageQuery()).total());
        verify(streams, times(1)).notifyUsers(List.of(2L), "read-changed", null);
    }

    @Test void idempotencyNormalizesRecipientOrderButRejectsChangedPayload() {
        Long first = service.publish(command("same", List.of(3L, 2L)));
        assertEquals(first, service.publish(command("same", List.of(2L, 3L, 2L))));
        assertThrows(ResponseStatusException.class, () -> service.publish(command("same", List.of(2L))));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM gak_message", Long.class));
    }

    @Test void concurrentRetryProducesOneMessage() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Long> send = () -> { start.await(); return service.publish(command("concurrent", List.of(2L))); };
            Future<Long> first = executor.submit(send), second = executor.submit(send);
            start.countDown();
            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM gak_message_recipient", Long.class));
        }
    }

    @Test void rollbackRemovesMessageAndNeverPushes() {
        transaction.executeWithoutResult(status -> {
            service.publish(command("rollback", List.of(2L)));
            verifyNoInteractions(streams);
            status.setRollbackOnly();
        });
        assertEquals(0, service.summary(2L).unreadCount());
        verifyNoInteractions(streams);
    }

    @Test void batchFailureRollsBackMessage() {
        jdbc.execute("ALTER TABLE gak_message_recipient ADD CONSTRAINT reject_test_recipient CHECK(user_id <> 3)");
        try { assertThrows(RuntimeException.class, () -> service.publish(command("batch", List.of(2L, 3L)))); }
        finally { jdbc.execute("ALTER TABLE gak_message_recipient DROP CONSTRAINT reject_test_recipient"); }
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM gak_message", Long.class));
        verifyNoInteractions(streams);
    }

    @Test void lostPushDoesNotLoseCommittedMessage() {
        doThrow(new IllegalStateException("模拟连接故障")).when(streams).notifyUsers(any(), any(), any());
        assertNotNull(service.publish(command("lost", List.of(2L))));
        assertEquals(1, service.summary(2L).unreadCount());
    }

    @Test void broadcastSnapshotAndAdminAuthorization() {
        var request = new SendMessageRequest("all", MessageAudience.ALL, List.of(), MessageCategory.ANNOUNCEMENT, MessagePriority.NORMAL, "公告", "正文");
        assertThrows(ResponseStatusException.class, () -> service.send(2L, request));
        assertThrows(ResponseStatusException.class, () -> service.sent(2L, new MessageQuery()));
        Long id = service.send(1L, request);
        jdbc.execute("INSERT INTO gak_user VALUES (5,'later','新用户',true,'ENABLED')");
        assertEquals(id, service.send(1L, request));
        assertEquals(3, service.recipients(1L, id, new MessageQuery()).total());
        assertEquals(4, service.recipientOptions(1L, new MessageQuery()).total());
        assertThrows(ResponseStatusException.class, () -> service.summary(4L));
    }

    @Test void allReadLeavesLaterMessageUnreadAndSupportsFilters() {
        service.publish(command("before", List.of(2L)));
        service.readAll(2L);
        service.publish(command("after", List.of(2L)));
        MessageQuery query = new MessageQuery(); query.setUnread(true); query.setCategory(MessageCategory.BUSINESS);
        assertEquals(1, service.inbox(2L, query).total());
        assertEquals(1L, service.summary(2L).categoryCounts().get("BUSINESS"));
        query.setCategory(MessageCategory.SECURITY);
        assertEquals(0, service.inbox(2L, query).total());
    }

    @Test void httpAuthenticationValidationAndRealtimeStream() throws Exception {
        String token = context.getBean(TokenService.class).issueToken(2L);
        String base = "http://localhost:" + context.getWebServer().getPort() + "/messages";
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(base + "/summary")).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            var forbidden = HttpRequest.newBuilder(URI.create(base + "/admin/sent")).header("Authorization", "Bearer " + token).GET().build();
            assertEquals(403, client.send(forbidden, HttpResponse.BodyHandlers.ofString()).statusCode());
            var invalid = HttpRequest.newBuilder(URI.create(base + "/inbox?pageSize=0")).header("Authorization", "Bearer " + token).GET().build();
            assertEquals(400, client.send(invalid, HttpResponse.BodyHandlers.ofString()).statusCode());
            var invalidCategory = HttpRequest.newBuilder(URI.create(base + "/inbox?category=UNKNOWN")).header("Authorization", "Bearer " + token).GET().build();
            assertEquals(400, client.send(invalidCategory, HttpResponse.BodyHandlers.ofString()).statusCode());
            var streamRequest = HttpRequest.newBuilder(URI.create(base + "/stream")).header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(5)).GET().build();
            var response = client.send(streamRequest, HttpResponse.BodyHandlers.ofInputStream());
            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("text/event-stream"));
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                assertEquals("event:ready", reader.readLine());
                while (!reader.readLine().isEmpty()) { /* 读完初始化帧 */ }
                Long id = service.publish(command("http-stream", List.of(2L)));
                try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
                    Future<String> received = executor.submit(() -> {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.contains("message-created")) return line;
                        }
                        return "";
                    });
                    assertEquals("event:message-created", received.get(2, TimeUnit.SECONDS));
                }
                assertNotNull(id);
            }
        }
    }

    @Test void allReadDoesNotCaptureUncommittedDelivery() throws Exception {
        service.publish(command("existing", List.of(2L)));
        CountDownLatch inserted = new CountDownLatch(1), commit = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> pending = executor.submit(() -> transaction.executeWithoutResult(status -> {
                service.publish(command("uncommitted", List.of(2L)));
                inserted.countDown();
                try { if (!commit.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("等待已读操作超时"); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
            }));
            assertTrue(inserted.await(5, TimeUnit.SECONDS));
            try { service.readAll(2L); }
            finally { commit.countDown(); }
            pending.get(5, TimeUnit.SECONDS);
            assertEquals(1, service.summary(2L).unreadCount());
        }
    }

    @Test void invalidRecipientAndUnknownTargetAreRejected() throws Exception {
        assertThrows(ResponseStatusException.class, () -> service.publish(command("invalid", List.of(4L))));
        assertThrows(ResponseStatusException.class, () -> service.publish(command("empty", List.of())));
        String encoded = context.getBean(ObjectMapper.class).writeValueAsString(command("url", List.of(2L))).replace("\"target\":null", "\"target\":\"https://evil.example\"");
        assertThrows(Exception.class, () -> context.getBean(ObjectMapper.class).readValue(encoded, PublishMessageCommand.class));
    }
}
