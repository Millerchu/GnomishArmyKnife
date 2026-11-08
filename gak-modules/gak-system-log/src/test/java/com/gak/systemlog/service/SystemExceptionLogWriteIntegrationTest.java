package com.gak.systemlog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.gak.framework.exception.GlobalExceptionHandler;
import com.gak.framework.exception.SystemExceptionCapture;
import com.gak.systemlog.mapper.SystemExceptionLogMapper;
import com.gak.systemlog.support.TemporaryPostgres16;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 独立 PG16 写入证明：显式 -Dgak.system-log.write-it=true 才运行。
 * 不启动 Web 服务，不注册故障端点，不使用外部数据库或凭据。
 */
@EnabledIfSystemProperty(named = "gak.system-log.write-it", matches = "true")
class SystemExceptionLogWriteIntegrationTest {

    private static final String APPLICATION_ROLE = "sl_test_application";
    private static final String SYNTHETIC_MESSAGE = "synthetic-exception-message";
    private static final String SYNTHETIC_CAUSE = "synthetic-cause-message";
    private static final String REQUEST_TEMPLATE = "/synthetic/items/{id}";
    private static final String EXCEPTION_TYPE = "java.lang.IllegalStateException";

    @Test
    void globalHandlerCommitsSanitizedLogThroughSpringProxyWithRestrictedRole() throws Exception {
        Path root = repositoryRoot();
        try (TemporaryPostgres16 postgres = TemporaryPostgres16.start(
                root.resolve("gak-modules/gak-system-log/target"))) {
            initializeSchema(root, postgres);
            DataSource applicationSource = new DriverManagerDataSource(postgres.jdbcUrl(), APPLICATION_ROLE, "");
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.registerBean(DataSource.class, () -> applicationSource);
                context.register(CaptureConfiguration.class);
                context.refresh();
                assertRestrictedRole(new JdbcTemplate(applicationSource));
                assertTrue(AopUtils.isAopProxy(context.getBean(SystemExceptionLogWriter.class)),
                        "写入器必须经真实事务代理调用");
                assertTrue(context.getBean(SystemExceptionCapture.class) instanceof PersistentSystemExceptionCapture);
                invokeHandlerInsideRolledBackTransaction(context);
                assertStoredLog(new JdbcTemplate(applicationSource));
            }
        }
    }

    /** 从模块或仓库根目录启动 Maven 均可定位原始脚本，不使用外部连接配置。 */
    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("gak-start/src/main/resources/schema.sql"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("找不到仓库 schema.sql");
    }

    private void initializeSchema(Path root, TemporaryPostgres16 postgres) throws Exception {
        JdbcTemplate owner = new JdbcTemplate(new DriverManagerDataSource(
                postgres.jdbcUrl(), TemporaryPostgres16.OWNER_ROLE, ""));
        String schema = Files.readString(root.resolve("gak-start/src/main/resources/schema.sql"));
        // 只执行真实表 DDL，避免 schema 中账号凭据和无关业务种子进入测试库。
        for (String table : List.of("gak_user", "gak_system_app", "gak_user_app_permission",
                "gak_system_exception_log")) {
            executeSchemaDdl(owner, schema, "CREATE TABLE IF NOT EXISTS " + table + " (");
        }
        executeSchemaDdl(owner, schema, "CREATE UNIQUE INDEX IF NOT EXISTS uk_system_app_code ");
        executeSchemaDdl(owner, schema, "CREATE UNIQUE INDEX IF NOT EXISTS uk_user_app_permission_user_code ");
        owner.execute(Files.readString(root.resolve("docs/sql/20261107-system-exception-log.sql")));
        owner.execute("CREATE ROLE " + APPLICATION_ROLE
                + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
        owner.execute("GRANT USAGE ON SCHEMA public TO " + APPLICATION_ROLE);
        String grant = Files.readString(root.resolve("docs/sql/20261107-system-exception-log-privileges.sql"))
                .replace(":\"app_role\"", "\"" + APPLICATION_ROLE + "\"");
        owner.execute(grant);
    }

    private void executeSchemaDdl(JdbcTemplate owner, String schema, String declaration) {
        var definition = Pattern.compile("(?ms)^" + Pattern.quote(declaration) + ".*?;").matcher(schema);
        assertTrue(definition.find(), "schema.sql 必须包含 " + declaration);
        owner.execute(definition.group());
    }

    private void assertRestrictedRole(JdbcTemplate jdbc) {
        assertEquals(APPLICATION_ROLE, jdbc.queryForObject("SELECT current_user", String.class));
        assertNotEquals(APPLICATION_ROLE, jdbc.queryForObject("""
                SELECT pg_get_userbyid(relowner) FROM pg_class
                WHERE oid = 'gak_system_exception_log'::regclass
                """, String.class));
        assertFalse(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls
                FROM pg_roles WHERE rolname = current_user
                """, Boolean.class)));
        for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER")) {
            boolean expected = "SELECT".equals(privilege) || "INSERT".equals(privilege);
            assertEquals(expected, jdbc.queryForObject(
                    "SELECT has_table_privilege(current_user, 'gak_system_exception_log', ?)",
                    Boolean.class, privilege), privilege);
        }
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM gak_system_exception_log", Long.class));
    }

    private void invokeHandlerInsideRolledBackTransaction(AnnotationConfigApplicationContext context) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/synthetic/items/synthetic-segment");
        request.setQueryString("marker=synthetic-query");
        request.addHeader("X-Trace-Id", "caller-supplied-trace");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, REQUEST_TEMPLATE);
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "synthetic-segment"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            TransactionTemplate transaction = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
            transaction.executeWithoutResult(status -> {
                var response = context.getBean(GlobalExceptionHandler.class).handleOther(syntheticFailure());
                assertEquals(500, response.getStatusCode().value());
                assertNotNull(response.getBody());
                assertEquals("500", response.getBody().code());
                assertEquals("服务器内部错误", response.getBody().message());
                assertFalse(response.getHeaders().containsKey("X-Trace-Id"));
                status.setRollbackOnly();
            });
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private IllegalStateException syntheticFailure() {
        IllegalArgumentException cause = new IllegalArgumentException(SYNTHETIC_CAUSE);
        cause.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("com.gak.synthetic.Repository", "load", "Repository.java", 7)});
        IllegalStateException failure = new IllegalStateException(SYNTHETIC_MESSAGE, cause);
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("com.gak.synthetic.Worker", "run", "Worker.java", 18)});
        return failure;
    }

    private void assertStoredLog(JdbcTemplate jdbc) {
        // 独立连接在外层事务回滚后读到唯一行，证明异常处理确实提交了记录。
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM gak_system_exception_log ORDER BY id");
        assertEquals(1, rows.size());
        Map<String, Object> stored = rows.getFirst();
        assertNotNull(stored.get("id"));
        assertNotNull(stored.get("occurred_at"));
        assertNotNull(stored.get("created_at"));
        assertEquals(EXCEPTION_TYPE, stored.get("exception_type"));
        assertEquals(EXCEPTION_TYPE + " at com.gak.synthetic.Worker.run(Worker.java:18)", stored.get("summary"));
        String stack = (String) stored.get("stack_trace");
        assertTrue(stack.startsWith(EXCEPTION_TYPE + "\n"));
        assertTrue(stack.contains("com.gak.synthetic.Worker.run(Worker.java:18)"));
        assertTrue(stack.contains("Caused by: java.lang.IllegalArgumentException"));
        assertTrue(stack.contains("com.gak.synthetic.Repository.load(Repository.java:7)"));
        assertFalse(stack.contains(SYNTHETIC_MESSAGE));
        assertFalse(stack.contains(SYNTHETIC_CAUSE));
        assertFalse(stack.contains("synthetic-segment"));
        assertFalse(stack.contains("synthetic-query"));
        assertFalse(stack.contains("caller-supplied-trace"));
        assertFalse(stack.contains(SystemExceptionSanitizer.TRUNCATION_MARKER));
        assertTrue(stack.length() <= SystemExceptionSanitizer.STACK_MAX);
        assertEquals(false, stored.get("stack_truncated"));
        assertEquals("HTTP", stored.get("source"));
        assertEquals("GET", stored.get("http_method"));
        assertEquals(REQUEST_TEMPLATE, stored.get("request_path"));
        assertTrue(((String) stored.get("trace_id")).matches("[0-9a-f]{32}"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({GlobalExceptionHandler.class, PersistentSystemExceptionCapture.class, SystemExceptionLogWriter.class})
    static class CaptureConfiguration {

        @Bean
        DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        SqlSessionTemplate sqlSessions(DataSource source) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(SystemExceptionLogMapper.class);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setConfiguration(configuration);
            return new SqlSessionTemplate(factory.getObject());
        }

        @Bean
        SystemExceptionLogMapper logMapper(SqlSessionTemplate sessions) {
            return sessions.getMapper(SystemExceptionLogMapper.class);
        }
    }
}
