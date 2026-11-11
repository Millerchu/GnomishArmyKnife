package com.gak.workbench.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.user.mapper.user.UserMapper;
import com.gak.workbench.dto.WorkbenchPreferenceRequest;
import com.gak.workbench.exception.WorkbenchPreferenceConflictException;
import com.gak.workbench.mapper.UserPreferenceMapper;
import com.gak.workbench.service.WorkbenchPayloadNormalizer;
import com.gak.workbench.service.WorkbenchPreferenceService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

/** 显式开启的真实 PG 用例，仅允许专用临时库 55491/wb2_verify；随机 schema 用后删除。 */
@EnabledIfEnvironmentVariable(named = "GAK_WORKBENCH_TEST_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:55491/wb2_verify")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkbenchPreferencePostgresTest {
    private final String schema = "wb_test_" + UUID.randomUUID().toString().replace("-", "");
    private JdbcTemplate owner;
    private JdbcTemplate jdbc;
    private UserPreferenceMapper mapper;
    private WorkbenchPreferenceService service;
    private TransactionTemplate transaction;

    @BeforeAll
    void setup() throws Exception {
        String url = System.getenv("GAK_WORKBENCH_TEST_URL");
        String username = System.getenv().getOrDefault("GAK_WORKBENCH_TEST_USER", "wb2_owner");
        owner = new JdbcTemplate(new DriverManagerDataSource(url, username, ""));
        owner.execute("CREATE SCHEMA " + schema);
        var source = new DriverManagerDataSource(url + "?currentSchema=" + schema, username, "");
        jdbc = new JdbcTemplate(source);
        String initialization = Files.readString(Path.of("../../gak-start/src/main/resources/schema.sql"));
        jdbc.execute(initialization.substring(0, initialization.indexOf(';') + 1));
        jdbc.execute("""
                INSERT INTO gak_user(id,username,password_hash,display_name,created_at,updated_at)
                VALUES (10,'wb_test_a','synthetic','A',now(),now()),(20,'wb_test_b','synthetic','B',now(),now())
                """);
        String ddl = Files.readString(Path.of("../../docs/sql/20261111-user-preference.sql"));
        // JDBC 不执行 psql 元命令；只在此用随机 schema 替换 public，避免触及共享表。
        ddl = ddl.replace("\\set ON_ERROR_STOP on", "").replace("public.", schema + ".");
        jdbc.execute(ddl);
        jdbc.execute(ddl);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(UserPreferenceMapper.class);
        configuration.addMapper(UserMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(UserPreferenceMapper.class);
        service = new WorkbenchPreferenceService(mapper, new WorkbenchPayloadNormalizer(),
                new SqlSessionTemplate(factory.getObject()).getMapper(UserMapper.class));
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    @BeforeEach
    void clearOnlyTestRows() {
        jdbc.execute("TRUNCATE gak_user_preference");
    }

    @AfterAll
    void cleanup() {
        if (owner != null) {
            owner.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void jsonbRoundTripManualVersionUpdateAndBothResultMappings() throws Exception {
        assertEquals(0, service.get(10L).version());
        var request = request(0);
        var created = transaction.execute(status -> service.save(10L, request));
        assertNotNull(created);
        assertEquals(1, created.version());
        assertEquals(created.payload(), service.get(10L).payload());
        assertEquals(0, service.get(20L).version());
        var row = mapper.find(10L, "workbench");
        assertEquals(created.payload(), mapper.selectById(row.getId()).getPayload());
        assertEquals("object", jdbc.queryForObject("SELECT jsonb_typeof(payload) FROM gak_user_preference", String.class));
        assertEquals(2, transaction.execute(status -> service.save(10L, request(1))).version());
        var conflict = assertThrows(WorkbenchPreferenceConflictException.class,
                () -> transaction.execute(status -> service.save(10L, request(1))));
        assertEquals(2, conflict.getLatest().version());
        assertEquals(service.get(10L).payload(), conflict.getLatest().payload());
    }

    @Test
    void concurrentCreatesProduceOneWinnerAndConflictWithReadableLatest() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var action = (java.util.concurrent.Callable<String>) () -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try {
                    transaction.execute(status -> service.save(10L, request(0)));
                    return "created";
                } catch (WorkbenchPreferenceConflictException conflict) {
                    assertEquals(1, conflict.getLatest().version());
                    assertNotNull(conflict.getLatest().payload());
                    return "conflict";
                }
            };
            var first = executor.submit(action);
            var second = executor.submit(action);
            start.countDown();
            assertEquals(java.util.Set.of("created", "conflict"),
                    java.util.Set.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)));
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM gak_user_preference", Integer.class));
    }

    private WorkbenchPreferenceRequest request(long version) {
        try {
            return new WorkbenchPreferenceRequest(version, 1, new ObjectMapper().readTree("""
                    {"cards":[{"id":"fuel","hidden":false,"size":"M","x":0,"y":0,"w":1,"h":1}],
                    "dock":{"collapsed":true,"order":["历史应用","todo-list"]}}
                    """));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
