package com.gak.systemlog.service;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gak.systemlog.config.SystemLogRetentionConfiguration;
import com.gak.systemlog.config.SystemLogRetentionProperties;
import com.gak.systemlog.support.TemporaryPostgres16;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 仅在本测试自起的空 PG16 中用非属主、无表级 DELETE 的应用角色验证清理。 */
@EnabledIfSystemProperty(named = "gak.system-log.retention-it", matches = "true")
class SystemExceptionLogRetentionIntegrationTest {

    private static final String APPLICATION_ROLE = "sl_retention_application";
    private static final String FUNCTION = "public.gak_purge_system_exception_log(timestamp, integer)";
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void retentionThroughSpringTransactionsWithRestrictedApplicationRole() throws Exception {
        Path root = repositoryRoot();
        try (TemporaryPostgres16 postgres = TemporaryPostgres16.start(root.resolve("gak-modules/gak-system-log/target"))) {
            DataSource ownerSource = new DriverManagerDataSource(postgres.jdbcUrl(), TemporaryPostgres16.OWNER_ROLE, "");
            JdbcTemplate owner = new JdbcTemplate(ownerSource);
            initialize(root, owner, postgres);
            DataSource appSource = new DriverManagerDataSource(postgres.jdbcUrl(), APPLICATION_ROLE, "");
            JdbcTemplate app = new JdbcTemplate(appSource);
            LocalDateTime cutoff = owner.queryForObject(
                    "SELECT (now() AT TIME ZONE 'Asia/Shanghai') - INTERVAL '30 days'", LocalDateTime.class);
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("retention-test", Map.of(
                        "gak.system-log.cleanup.enabled", "true", "gak.system-log.cleanup.batch-interval-ms", "0")));
                context.registerBean(DataSource.class, () -> appSource);
                context.registerBean("systemLogClock", Clock.class, () -> Clock.fixed(
                        cutoff.plusDays(30).atZone(SHANGHAI).toInstant(), SHANGHAI));
                context.register(SystemExceptionLogWriteIntegrationTest.CaptureConfiguration.class,
                        SystemLogRetentionConfiguration.class);
                context.refresh();
                SystemExceptionLogPurger purger = context.getBean(SystemExceptionLogPurger.class);
                SystemExceptionLogRetentionJob job = context.getBean(SystemExceptionLogRetentionJob.class);
                assertTrue(AopUtils.isAopProxy(purger), "每批必须经过真实 REQUIRES_NEW 事务代理");
                assertPrivileges(owner, app);
                boundaries(owner, app, purger, cutoff);
                invalidArgumentsAndHardFloor(owner, app, cutoff);
                batches(owner, app, context, cutoff);
                lockContention(ownerSource, owner, app, purger, cutoff);
                tableLockTimeout(ownerSource, owner, app, job, context.getBean(SystemLogRetentionProperties.class), cutoff);
                safePermissionAndMissingFunctionFailures(root, postgres, owner, app, job, cutoff);
                explainIndex(ownerSource, owner, cutoff);
            }
        }
    }

    private void explainIndex(DataSource ownerSource, JdbcTemplate owner, LocalDateTime cutoff) {
        String query = "EXPLAIN SELECT id FROM public.gak_system_exception_log "
                + "WHERE occurred_at < ? ORDER BY occurred_at, id LIMIT 500";
        String plan = owner.queryForList(query, cutoff).toString();
        // 同一事务绑定同一连接；小样本关闭顺序扫描仅用于确认已有索引可用。
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(ownerSource));
        String indexPlan = transaction.execute(status -> {
            owner.execute("SET LOCAL enable_seqscan = off");
            return owner.queryForList(query, cutoff).toString();
        });
        assertTrue(indexPlan.contains("idx_system_exception_log_occurred"), indexPlan);
        System.out.println("Retention EXPLAIN: " + plan + "; index capability: " + indexPlan);
    }

    private void initialize(Path root, JdbcTemplate owner, TemporaryPostgres16 postgres) throws Exception {
        String schema = Files.readString(root.resolve("gak-start/src/main/resources/schema.sql"));
        var definition = java.util.regex.Pattern.compile(
                "(?ms)^CREATE TABLE IF NOT EXISTS gak_system_exception_log \\(.*?;").matcher(schema);
        assertTrue(definition.find());
        owner.execute(definition.group());
        owner.execute("CREATE INDEX idx_system_exception_log_occurred ON public.gak_system_exception_log(occurred_at DESC,id DESC)");
        owner.execute("CREATE ROLE " + APPLICATION_ROLE + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT");
        owner.execute("GRANT USAGE ON SCHEMA public TO " + APPLICATION_ROLE);
        owner.execute(Files.readString(root.resolve("docs/sql/20261107-system-exception-log-privileges.sql"))
                .replace(":\"app_role\"", "\"" + APPLICATION_ROLE + "\""));
        installRetention(root, postgres);
        installRetention(root, postgres);
    }

    private void installRetention(Path root, TemporaryPostgres16 postgres) throws Exception {
        postgres.executeScript(root.resolve("docs/sql/20261111-system-exception-log-retention.sql"),
                "app_role=" + APPLICATION_ROLE);
    }

    private void assertPrivileges(JdbcTemplate owner, JdbcTemplate app) {
        assertNotEquals(APPLICATION_ROLE, app.queryForObject("""
                SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid='public.gak_system_exception_log'::regclass
                """, String.class));
        for (String privilege : List.of("SELECT", "INSERT", "DELETE", "UPDATE", "TRUNCATE")) {
            assertEquals(List.of("SELECT", "INSERT").contains(privilege), app.queryForObject(
                    "SELECT has_table_privilege(current_user,'public.gak_system_exception_log',?)", Boolean.class, privilege));
        }
        assertTrue(app.queryForObject("SELECT has_function_privilege(current_user, ?, 'EXECUTE')", Boolean.class, FUNCTION));
        assertTrue(owner.queryForObject("""
                SELECT 'search_path=pg_catalog, pg_temp' = ANY(p.proconfig)
                FROM pg_catalog.pg_proc p
                WHERE p.oid='public.gak_purge_system_exception_log(timestamp,integer)'::regprocedure
                """, Boolean.class), "SECURITY DEFINER 不得搜索可写 public，且 pg_temp 必须显式放最后");
        assertEquals(0, owner.queryForObject("""
                SELECT count(*) FROM pg_proc p, LATERAL aclexplode(p.proacl) a
                WHERE p.oid = 'public.gak_purge_system_exception_log(timestamp,integer)'::regprocedure
                  AND a.grantee = 0 AND a.privilege_type = 'EXECUTE'
                """, Integer.class));
        assertTrue(owner.queryForObject("""
                SELECT p.proowner=c.relowner AND p.prosecdef
                FROM pg_proc p CROSS JOIN pg_class c
                WHERE p.oid='public.gak_purge_system_exception_log(timestamp,integer)'::regprocedure
                  AND c.oid='public.gak_system_exception_log'::regclass
                """, Boolean.class));
    }

    private void boundaries(JdbcTemplate owner, JdbcTemplate app, SystemExceptionLogPurger purger, LocalDateTime cutoff) {
        insert(owner, 1, cutoff.minusNanos(1000));
        insert(owner, 2, cutoff);
        insert(owner, 3, cutoff.plusNanos(1000));
        assertEquals(1, purger.purgeOneBatch(cutoff, 500).orElseThrow());
        assertEquals(List.of(2L, 3L), app.queryForList("SELECT id FROM public.gak_system_exception_log ORDER BY id", Long.class));
        assertEquals(0, purger.purgeOneBatch(cutoff, 500).orElseThrow());
        owner.execute("TRUNCATE public.gak_system_exception_log");
        assertEquals(0, purger.purgeOneBatch(cutoff, 500).orElseThrow());
    }

    private void invalidArgumentsAndHardFloor(JdbcTemplate owner, JdbcTemplate app, LocalDateTime cutoff) {
        insert(owner, 1, cutoff.minusDays(1));
        insert(owner, 2, cutoff.plusDays(1));
        for (Integer limit : new Integer[] {null, 0, -1, 1001}) {
            assertThrows(org.springframework.dao.DataAccessException.class, () -> app.queryForObject(
                    "SELECT public.gak_purge_system_exception_log(?, ?)", Integer.class, cutoff.plusDays(30), limit));
        }
        assertThrows(org.springframework.dao.DataAccessException.class, () -> app.queryForObject(
                "SELECT public.gak_purge_system_exception_log(CAST(NULL AS timestamp), 500)", Integer.class));
        assertEquals(2, count(app));
        assertEquals(1, app.queryForObject("SELECT public.gak_purge_system_exception_log(?, 500)",
                Integer.class, cutoff.plusDays(30)));
        assertEquals(List.of(2L), app.queryForList("SELECT id FROM public.gak_system_exception_log ORDER BY id", Long.class));
        owner.execute("TRUNCATE public.gak_system_exception_log");
    }

    private void batches(JdbcTemplate owner, JdbcTemplate app, AnnotationConfigApplicationContext context, LocalDateTime cutoff) {
        owner.update("""
                INSERT INTO public.gak_system_exception_log(id,occurred_at,created_at,trace_id,source,exception_type,summary,stack_trace,stack_truncated)
                SELECT n, ?, ?, 'synthetic-' || n, 'HTTP', 'Synthetic', 'synthetic', 'synthetic', false
                FROM generate_series(1,2500) n
                """, cutoff.minusDays(1), cutoff.minusDays(1));
        var purger = context.getBean(SystemExceptionLogPurger.class);
        for (int batch = 0; batch < 5; batch++) {
            assertEquals(500, purger.purgeOneBatch(cutoff, 500).orElseThrow());
            assertEquals(2500 - (batch + 1) * 500, count(app));
        }
        assertEquals(0, purger.purgeOneBatch(cutoff, 500).orElseThrow());
        for (int id = 1; id <= 5; id++) {
            insert(owner, id, cutoff.minusDays(1));
        }
        var properties = context.getBean(SystemLogRetentionProperties.class);
        properties.setBatchSize(2);
        properties.setMaxBatchesPerRun(2);
        context.getBean(SystemExceptionLogRetentionJob.class).cleanup();
        assertEquals(1, count(app));
        context.getBean(SystemExceptionLogRetentionJob.class).cleanup();
        assertEquals(0, count(app));
        properties.setBatchSize(500);
        properties.setMaxBatchesPerRun(40);
    }

    private void lockContention(DataSource ownerSource, JdbcTemplate owner, JdbcTemplate app,
            SystemExceptionLogPurger purger, LocalDateTime cutoff) throws Exception {
        insert(owner, 1, cutoff.minusDays(1));
        try (Connection holder = ownerSource.getConnection()) {
            holder.setAutoCommit(false);
            try (var statement = holder.createStatement()) {
                // 与生产常量一致的双 int4 组合，锁持有于另一会话。
                statement.execute("SELECT pg_advisory_xact_lock(" + 0x47414B + "," + 0x534C + ")");
            }
            assertTrue(purger.purgeOneBatch(cutoff, 500).isEmpty());
            assertEquals(1, count(app));
            holder.rollback();
        }
        assertEquals(1, purger.purgeOneBatch(cutoff, 500).orElseThrow());
    }

    private void tableLockTimeout(DataSource ownerSource, JdbcTemplate owner, JdbcTemplate app,
            SystemExceptionLogRetentionJob job, SystemLogRetentionProperties properties, LocalDateTime cutoff) throws Exception {
        insert(owner, 1, cutoff.minusDays(1));
        Logger logger = (Logger) LoggerFactory.getLogger(SystemExceptionLogRetentionJob.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try (Connection holder = ownerSource.getConnection()) {
            holder.setAutoCommit(false);
            try (var statement = holder.createStatement()) {
                statement.execute("LOCK TABLE public.gak_system_exception_log IN ACCESS EXCLUSIVE MODE");
            }
            long started = System.nanoTime();
            assertDoesNotThrow(job::cleanup);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            assertTrue(elapsedMs >= 1500 && elapsedMs < 5000, "锁等待应受约 2 秒超时限制: " + elapsedMs);
            assertTrue(events.list.stream().anyMatch(event -> event.getFormattedMessage().contains("sqlState=55P03")),
                    "ACCESS EXCLUSIVE 阻塞应记录 55P03 且不向调度器抛出");
            events.list.clear();
            // 同一阻塞条件下缩短语句超时，确认其在函数调用前设置并先于 lock_timeout 生效。
            properties.setStatementTimeout(java.time.Duration.ofMillis(100));
            try {
                started = System.nanoTime();
                assertDoesNotThrow(job::cleanup);
                elapsedMs = (System.nanoTime() - started) / 1_000_000;
                assertTrue(elapsedMs >= 50 && elapsedMs < 1500, "语句超时应先于锁超时: " + elapsedMs);
                assertTrue(events.list.stream().anyMatch(event -> event.getFormattedMessage().contains("sqlState=57014")));
            } finally {
                properties.setStatementTimeout(java.time.Duration.ofSeconds(10));
            }
            holder.rollback();
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
        assertEquals(1, count(app));
        insert(app, 2, cutoff.plusDays(30));
        assertEquals(2, count(app));
        assertEquals(0, schedulerCount(app));
        owner.execute("TRUNCATE public.gak_system_exception_log");
    }

    private void safePermissionAndMissingFunctionFailures(Path root, TemporaryPostgres16 postgres,
            JdbcTemplate owner, JdbcTemplate app,
            SystemExceptionLogRetentionJob job, LocalDateTime cutoff) throws Exception {
        insert(owner, 1, cutoff.minusDays(1));
        owner.execute("REVOKE EXECUTE ON FUNCTION " + FUNCTION + " FROM " + APPLICATION_ROLE);
        assertDoesNotThrow(job::cleanup);
        assertEquals(1, count(app));
        assertEquals(0, schedulerCount(app));
        owner.execute("DROP FUNCTION " + FUNCTION);
        assertDoesNotThrow(job::cleanup);
        assertEquals(1, count(app));
        assertEquals(0, schedulerCount(app));
        installRetention(root, postgres);
        assertDoesNotThrow(job::cleanup);
        assertEquals(0, count(app));
    }

    private void insert(JdbcTemplate jdbc, long id, LocalDateTime occurred) {
        jdbc.update("""
                INSERT INTO public.gak_system_exception_log(id,occurred_at,created_at,trace_id,source,exception_type,summary,stack_trace,stack_truncated)
                VALUES (?, ?, ?, 'synthetic', 'HTTP', 'Synthetic', 'synthetic', 'synthetic', false)
                """, id, occurred, occurred);
    }

    private int count(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT count(*) FROM public.gak_system_exception_log", Integer.class);
    }

    private int schedulerCount(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT count(*) FROM public.gak_system_exception_log WHERE source='SCHEDULER'", Integer.class);
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isRegularFile(current.resolve("gak-start/src/main/resources/schema.sql"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("找不到仓库 schema.sql");
        }
        return current;
    }
}
