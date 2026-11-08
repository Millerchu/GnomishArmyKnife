package com.gak.systemlog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.gak.framework.exception.GlobalExceptionHandler;
import com.gak.framework.exception.SystemExceptionCapture;
import com.gak.systemlog.domain.SystemExceptionLog;
import com.gak.systemlog.dto.SystemExceptionLogQuery;
import com.gak.systemlog.mapper.SystemExceptionLogMapper;
import com.gak.user.mapper.user.UserMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;

/**
 * 真实 PostgreSQL + MyBatis 回归：DDL owner 与应用运行角色必须不同。
 * 仅连接显式指定的临时 trust 认证数据库，不读取密码或登录 token。
 * 连接用户须可创建 schema / NOLOGIN 角色；只清理本测试创建的随机 schema 和角色。
 */
@EnabledIfEnvironmentVariable(named = "GAK_SYSTEM_LOG_TEST_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SystemExceptionLogPostgresIntegrationTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long USER_ID = 2L;
    private static final Long UNKNOWN_ID = 999999999999999999L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 11, 7, 12, 0);
    private static final String EXCEPTION_TYPE = "java.lang.IllegalStateException";
    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
    private static final Path REPOSITORY_ROOT = Path.of("../..");

    private final String suffix = UUID.randomUUID().toString().replace("-", "");
    private final String schema = "sl_bug_01_" + suffix;
    private final String runtimeRole = "sl_reader_" + suffix;
    private JdbcTemplate ownerJdbc;
    private JdbcTemplate runtimeJdbc;
    private SystemExceptionLogMapper logMapper;
    private SystemExceptionLogService service;
    private String grantSql;
    private boolean schemaCreated;
    private boolean roleCreated;

    @BeforeAll
    void createIsolatedSchemaAndRuntimeRole() throws Exception {
        String url = System.getenv("GAK_SYSTEM_LOG_TEST_URL");
        String user = System.getenv().getOrDefault("GAK_SYSTEM_LOG_TEST_USER", System.getProperty("user.name"));
        DataSource ownerSource = new DriverManagerDataSource(url, user, "");
        ownerJdbc = new JdbcTemplate(ownerSource);
        ownerJdbc.execute("CREATE SCHEMA " + schema);
        schemaCreated = true;
        ownerJdbc.execute("CREATE ROLE " + runtimeRole + " NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT");
        roleCreated = true;
        // DriverManagerDataSource 每次打开新连接，所有 DDL 都需使用固定的隔离 search_path。
        DataSource schemaOwnerSource = scopedSource(ownerSource, false);
        ownerJdbc = new JdbcTemplate(schemaOwnerSource);
        createPermissionFixtures();
        ownerJdbc.execute(Files.readString(REPOSITORY_ROOT.resolve("docs/sql/20261107-system-exception-log.sql")));
        ownerJdbc.execute("GRANT USAGE ON SCHEMA " + schema + " TO " + runtimeRole);
        ownerJdbc.execute("GRANT SELECT ON gak_user, gak_system_app, gak_user_app_permission TO " + runtimeRole);
        ownerJdbc.update("""
                INSERT INTO gak_user_app_permission(id,user_id,app_id,app_code,granted,created_at,updated_at)
                VALUES (2,2,2022,'APP_SYSTEM_LOG',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        grantSql = Files.readString(REPOSITORY_ROOT.resolve("docs/sql/20261107-system-exception-log-privileges.sql"))
                .replace(":\"app_role\"", "\"" + runtimeRole + "\"");

        DataSource runtimeSource = scopedSource(ownerSource, true);
        runtimeJdbc = new JdbcTemplate(runtimeSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(SystemExceptionLogMapper.class);
        configuration.addMapper(UserMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(runtimeSource);
        factory.setConfiguration(configuration);
        SqlSessionTemplate sessions = new SqlSessionTemplate(factory.getObject());
        logMapper = sessions.getMapper(SystemExceptionLogMapper.class);
        service = new SystemExceptionLogService(logMapper, sessions.getMapper(UserMapper.class));
    }

    private DataSource scopedSource(DataSource ownerSource, boolean runtime) {
        return new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                Connection connection = ownerSource.getConnection();
                try (var statement = connection.createStatement()) {
                    statement.execute("SET search_path TO " + schema);
                    if (runtime) {
                        statement.execute("SET ROLE " + runtimeRole);
                    }
                    return connection;
                } catch (SQLException failure) {
                    connection.close();
                    throw failure;
                }
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                throw new SQLException("测试仅支持临时库 trust 连接");
            }
        };
    }

    /** 只创建权限查询所需的合成用户和应用元数据，不读取现有账号。 */
    private void createPermissionFixtures() {
        ownerJdbc.execute("""
                CREATE TABLE gak_user (
                    id BIGINT PRIMARY KEY, username VARCHAR(64), password_hash VARCHAR(255), display_name VARCHAR(100),
                    phone VARCHAR(20), email VARCHAR(100), role_code VARCHAR(20), status VARCHAR(20), enabled BOOLEAN,
                    force_change_password BOOLEAN, last_login_time TIMESTAMP, remark VARCHAR(255),
                    created_at TIMESTAMP, updated_at TIMESTAMP
                );
                CREATE TABLE gak_system_app (
                    id BIGINT PRIMARY KEY, app_code VARCHAR(64) UNIQUE, app_name VARCHAR(64), route_path VARCHAR(128),
                    category VARCHAR(64), data_source_mode VARCHAR(16), icon_type VARCHAR(16), icon_text VARCHAR(32),
                    security_level VARCHAR(20), encryption_mode VARCHAR(20), enabled BOOLEAN, sort_no INTEGER,
                    description VARCHAR(255), created_at TIMESTAMP, updated_at TIMESTAMP
                );
                CREATE TABLE gak_user_app_permission (
                    id BIGINT PRIMARY KEY, user_id BIGINT, app_id BIGINT, app_code VARCHAR(64), granted BOOLEAN,
                    granted_by BIGINT, granted_at TIMESTAMP, remark VARCHAR(255), created_at TIMESTAMP, updated_at TIMESTAMP
                );
                INSERT INTO gak_user(id,username,role_code,status,enabled) VALUES
                    (1,'admin','ADMIN','ENABLED',TRUE), (2,'synthetic_user','USER','ENABLED',TRUE);
                """);
    }

    @BeforeEach
    void restoreSyntheticFixturesAndGrant() {
        ownerJdbc.execute("TRUNCATE gak_system_exception_log");
        ownerJdbc.update("UPDATE gak_system_app SET enabled=TRUE");
        ownerJdbc.update("UPDATE gak_user_app_permission SET granted=TRUE");
        ownerJdbc.execute(grantSql);
    }

    @AfterAll
    void dropOnlyOwnedTestObjects() {
        if (ownerJdbc == null) {
            return;
        }
        if (schemaCreated) {
            ownerJdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
        if (roleCreated) {
            ownerJdbc.execute("DROP ROLE " + runtimeRole);
        }
    }

    @Test
    void reproduces42501BeforeGrantAndRepairsOnlyRequiredPrivileges() {
        ownerJdbc.execute("REVOKE SELECT, INSERT ON gak_system_exception_log FROM " + runtimeRole);
        assertPermissionDenied(() -> service.page(ADMIN_ID, new SystemExceptionLogQuery()));
        assertPermissionDenied(() -> service.detail(ADMIN_ID, UNKNOWN_ID));
        assertPermissionDenied(() -> logMapper.insert(row(10L, "synthetic")));

        ownerJdbc.execute(grantSql);
        ownerJdbc.execute(grantSql);

        assertEquals(runtimeRole, runtimeJdbc.queryForObject("SELECT current_user", String.class));
        assertTrue(hasPrivilege("SELECT"));
        assertTrue(hasPrivilege("INSERT"));
        assertFalse(hasPrivilege("UPDATE"));
        assertFalse(hasPrivilege("DELETE"));
        assertFalse(runtimeJdbc.queryForObject("""
                SELECT pg_get_userbyid(relowner) = current_user
                FROM pg_class WHERE oid='gak_system_exception_log'::regclass
                """, Boolean.class));
        assertEquals(0L, service.page(ADMIN_ID, new SystemExceptionLogQuery()).total());
        assertEquals(1, logMapper.insert(row(10L, "synthetic")));
        assertEquals(1L, service.page(ADMIN_ID, new SystemExceptionLogQuery()).total());
    }

    @Test
    void defaultAndMaximumPageUseStableOrderAndMapListColumns() {
        for (long id = 1; id <= 25; id++) {
            logMapper.insert(row(id, "Synthetic " + id));
        }
        var first = service.page(ADMIN_ID, new SystemExceptionLogQuery());
        assertEquals(25L, first.total());
        assertEquals(20, first.list().size());
        assertEquals(25L, first.list().getFirst().id());
        assertEquals(6L, first.list().getLast().id());
        assertEquals(OCCURRED_AT, first.list().getFirst().occurredAt());
        assertEquals(TRACE_ID, first.list().getFirst().traceId());
        assertEquals(EXCEPTION_TYPE, first.list().getFirst().exceptionType());
        assertEquals("HTTP", first.list().getFirst().source());
        assertEquals("GET", first.list().getFirst().httpMethod());
        assertEquals("/synthetic/{id}", first.list().getFirst().requestPath());
        assertEquals("Synthetic 25", first.list().getFirst().summary());

        SystemExceptionLogQuery query = new SystemExceptionLogQuery();
        query.setPageNo(2);
        assertEquals(List.of(5L, 4L, 3L, 2L, 1L),
                service.page(ADMIN_ID, query).list().stream().map(item -> item.id()).toList());
        query.setPageNo(1);
        query.setPageSize(100);
        assertEquals(25, service.page(ADMIN_ID, query).list().size());
    }

    @Test
    void keywordMatchesCaseInsensitivelyAndEscapesLiteralWildcards() {
        logMapper.insert(row(1L, "Synthetic 100%_ Path\\Marker"));
        logMapper.insert(row(2L, "Synthetic 100ab PathXMarker"));
        for (String keyword : List.of("100%_", "%", "_", "\\", "pAtH\\mArKeR")) {
            SystemExceptionLogQuery query = new SystemExceptionLogQuery();
            query.setKeyword(keyword);
            var result = service.page(ADMIN_ID, query);
            assertEquals(1L, result.total(), keyword);
            assertEquals(1L, result.list().getFirst().id(), keyword);
        }
        SystemExceptionLogQuery query = new SystemExceptionLogQuery();
        query.setKeyword("%' OR 1=1 --");
        assertEquals(0L, service.page(ADMIN_ID, query).total());
    }

    @Test
    void typeTraceAndInclusiveTimeFiltersApplyToCountAndList() {
        logMapper.insert(row(1L, "Synthetic match"));
        SystemExceptionLog other = row(2L, "Synthetic other");
        other.setTraceId("fedcba9876543210fedcba9876543210");
        other.setExceptionType("java.lang.IllegalArgumentException");
        other.setOccurredAt(OCCURRED_AT.minusSeconds(1));
        logMapper.insert(other);
        SystemExceptionLogQuery query = new SystemExceptionLogQuery();
        query.setExceptionType(EXCEPTION_TYPE);
        assertSingleMatch(query);
        query.setExceptionType(null);
        query.setTraceId(TRACE_ID);
        assertSingleMatch(query);
        query.setExceptionType(EXCEPTION_TYPE);
        query.setOccurredFrom(OCCURRED_AT);
        query.setOccurredTo(OCCURRED_AT);
        assertSingleMatch(query);
        query.setTraceId("not-an-existing-trace");
        assertEquals(0L, service.page(ADMIN_ID, query).total());
        assertTrue(service.page(ADMIN_ID, query).list().isEmpty());
    }

    @Test
    void detailMapsStackBooleanTimestampAndNullableBackgroundRequest() {
        SystemExceptionLog stored = row(1L, "Synthetic detail");
        stored.setSource("SCHEDULER");
        stored.setHttpMethod(null);
        stored.setRequestPath(null);
        stored.setStackTruncated(true);
        logMapper.insert(stored);
        var detail = service.detail(ADMIN_ID, 1L);
        assertEquals(stored.getId(), detail.id());
        assertEquals(OCCURRED_AT, detail.occurredAt());
        assertEquals(TRACE_ID, detail.traceId());
        assertEquals(EXCEPTION_TYPE, detail.exceptionType());
        assertEquals("Synthetic detail", detail.summary());
        assertEquals(stored.getStackTrace(), detail.stackTrace());
        assertTrue(detail.stackTruncated());
        assertEquals("SCHEDULER", detail.source());
        assertNull(detail.httpMethod());
        assertNull(detail.requestPath());
        assertEquals(OCCURRED_AT, logMapper.selectById(1L).getCreatedAt());
        logMapper.insert(row(2L, "Synthetic untruncated"));
        assertFalse(service.detail(ADMIN_ID, 2L).stackTruncated());
    }

    @Test
    void missingNumericIdBecomes404AndReaderPermissionsRemainEnforced() {
        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
                () -> service.detail(ADMIN_ID, UNKNOWN_ID));
        var handler = new GlobalExceptionHandler(
                new StaticListableBeanFactory().getBeanProvider(SystemExceptionCapture.class));
        var response = handler.handleResponseStatus(missing);
        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("404", response.getBody().code());
        assertEquals("系统日志不存在", response.getBody().message());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.detail(USER_ID, UNKNOWN_ID)).getStatusCode().value());
        ownerJdbc.update("UPDATE gak_user_app_permission SET granted=FALSE WHERE user_id=?", ADMIN_ID);
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.page(ADMIN_ID, new SystemExceptionLogQuery())).getStatusCode().value());
        ownerJdbc.update("UPDATE gak_user_app_permission SET granted=TRUE WHERE user_id=?", ADMIN_ID);
        ownerJdbc.update("UPDATE gak_system_app SET enabled=FALSE");
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.detail(ADMIN_ID, UNKNOWN_ID)).getStatusCode().value());
    }

    private void assertSingleMatch(SystemExceptionLogQuery query) {
        var result = service.page(ADMIN_ID, query);
        assertEquals(1L, result.total());
        assertEquals(List.of(1L), result.list().stream().map(item -> item.id()).toList());
    }

    private boolean hasPrivilege(String privilege) {
        return Boolean.TRUE.equals(runtimeJdbc.queryForObject(
                "SELECT has_table_privilege(current_user,'gak_system_exception_log',?)", Boolean.class, privilege));
    }

    private void assertPermissionDenied(Runnable query) {
        Throwable cause = assertThrows(DataAccessException.class, query::run);
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertTrue(cause instanceof SQLException);
        assertEquals("42501", ((SQLException) cause).getSQLState());
    }

    private SystemExceptionLog row(Long id, String summary) {
        SystemExceptionLog stored = new SystemExceptionLog();
        stored.setId(id);
        stored.setOccurredAt(OCCURRED_AT);
        stored.setTraceId(TRACE_ID);
        stored.setSource("HTTP");
        stored.setExceptionType(EXCEPTION_TYPE);
        stored.setSummary(summary);
        stored.setStackTrace("java.lang.IllegalStateException\n\tat com.gak.synthetic.Worker.run(Worker.java:1)");
        stored.setStackTruncated(false);
        stored.setHttpMethod("GET");
        stored.setRequestPath("/synthetic/{id}");
        stored.setCreatedAt(OCCURRED_AT);
        return stored;
    }
}
