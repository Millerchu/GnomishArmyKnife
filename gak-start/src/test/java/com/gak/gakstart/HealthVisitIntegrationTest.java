package com.gak.gakstart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.healthrecord.domain.HealthVisit;
import com.gak.healthrecord.dto.SaveHealthVisitRequest;
import com.gak.healthrecord.mapper.HealthVisitMapper;
import com.gak.healthrecord.service.HealthRecordService;
import com.gak.personalbills.domain.PersonalBill;
import com.gak.personalbills.mapper.PersonalBillMapper;
import com.gak.personalbills.service.PersonalBillService;
import com.gak.framework.exception.BusinessException;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

/** 显式开启后只在本机随机独立 schema 验证就诊账单事务，不初始化业务 schema。 */
@SpringBootTest(properties = {"gak.attachment.legacy-migration-enabled=false", "spring.sql.init.mode=always"})
@EnabledIfSystemProperty(named = "gak.health.db-test", matches = "true")
class HealthVisitIntegrationTest {
    private static final String SCHEMA = "health_test_" + UUID.randomUUID().toString().replace("-", "");
    private static String jdbcUrl;
    private static String username;
    private static String password;
    private static boolean schemaCreated;
    @Autowired private HealthRecordService service;
    @Autowired private HealthVisitMapper records;
    @Autowired private PersonalBillMapper bills;
    @Autowired private PersonalBillService billService;
    @Autowired private UserMapper users;
    @Autowired private JdbcTemplate jdbc;
    private Long owner;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = HealthVisitIntegrationTest.class.getResourceAsStream("/application.properties")) {
            properties.load(input);
        }
        StandardEnvironment environment = new StandardEnvironment();
        jdbcUrl = environment.resolvePlaceholders(properties.getProperty("spring.datasource.url"));
        username = environment.resolvePlaceholders(properties.getProperty("spring.datasource.username"));
        password = environment.resolvePlaceholders(properties.getProperty("spring.datasource.password"));
        if (!jdbcUrl.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/[^?]+")) {
            throw new IllegalStateException("就诊集成测试仅允许无额外参数的本机 PostgreSQL URL");
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            connection.createStatement().execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }
        String directory = Files.createTempDirectory("gak-health-test-").toString();
        registry.add("spring.datasource.url", () -> jdbcUrl + "?currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("gak.attachment.storage-root", () -> directory);
    }

    @BeforeEach
    void createOwner() {
        User user = new User();
        user.setUsername("health-test-" + UUID.randomUUID());
        user.setPasswordHash("test-not-a-login-password");
        user.setDisplayName("就诊集成测试");
        user.setRoleCode("USER");
        user.setStatus("ENABLED");
        user.setEnabled(true);
        user.setForceChangePassword(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        users.insert(user);
        owner = user.getId();
    }

    @Test
    void shouldReapplySchemaWithoutBackfillingOldVisits() {
        var historical = service.createVisit(owner, request(null));
        var paid = service.createVisit(owner, request("128.50"));
        Long billId = linked(paid.getId()).getFirst().getId();
        String schema;
        try (InputStream input = getClass().getResourceAsStream("/schema.sql")) {
            schema = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("无法读取就诊金额升级 SQL", exception);
        }
        String upgrade = schema.substring(schema.indexOf("-- 2026-10-04 就诊实际自付金额"));
        var initializer = new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ByteArrayResource(upgrade.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        initializer.execute(jdbc.getDataSource());
        initializer.execute(jdbc.getDataSource());
        assertNull(records.selectById(historical.getId()).getAmount());
        assertTrue(linked(historical.getId()).isEmpty());
        assertEquals(billId, linked(paid.getId()).getFirst().getId());
        assertEquals(new BigDecimal("128.50"), records.selectById(paid.getId()).getAmount());
    }

    @Test
    void shouldPersistAmountAndKeepSameBillOnRepeatedEditThenDelete() {
        var saved = service.createVisit(owner, request("128.50"));
        assertEquals(new BigDecimal("128.50"), records.selectById(saved.getId()).getAmount());
        PersonalBill bill = linked(saved.getId()).getFirst();
        assertEquals("医疗", bill.getCategoryName());
        assertEquals("CNY", bill.getCurrency());
        assertEquals("医院就诊", bill.getNote());
        assertEquals("测试医院", bill.getMerchantName());
        assertThrows(BusinessException.class, () -> billService.delete(owner, bill.getId()));
        com.gak.personalbills.dto.SavePersonalBillRequest billEdit = new com.gak.personalbills.dto.SavePersonalBillRequest();
        assertThrows(BusinessException.class, () -> billService.update(owner, bill.getId(), billEdit));
        SaveHealthVisitRequest edit = request("100.00");
        edit.setHospitalName("复诊医院");
        edit.setVisitDate(LocalDate.of(2026, 10, 3));
        service.updateVisit(owner, saved.getId(), edit);
        service.updateVisit(owner, saved.getId(), edit);
        assertEquals(1, linked(saved.getId()).size());
        assertEquals(bill.getId(), linked(saved.getId()).getFirst().getId());
        assertEquals(new BigDecimal("100.00"), linked(saved.getId()).getFirst().getAmount());
        assertEquals("复诊医院", linked(saved.getId()).getFirst().getMerchantName());
        assertEquals(edit.getVisitDate(), linked(saved.getId()).getFirst().getBillDate());
        assertEquals(new BigDecimal("100.00"), service.getVisit(owner, saved.getId()).getAmount());
        service.deleteVisit(owner, saved.getId());
        assertNull(records.selectById(saved.getId()));
        assertTrue(linked(saved.getId()).isEmpty());
    }

    @Test
    void shouldBillHistoricalVisitWhenFilledAndPersistNullWhenCleared() {
        var historical = service.createVisit(owner, request(null));
        assertTrue(linked(historical.getId()).isEmpty());
        service.updateVisit(owner, historical.getId(), request("128.50"));
        assertEquals(1, linked(historical.getId()).size());
        service.updateVisit(owner, historical.getId(), request(null));
        assertNull(records.selectById(historical.getId()).getAmount());
        assertNull(service.getVisit(owner, historical.getId()).getAmount());
        assertTrue(linked(historical.getId()).isEmpty());
        service.updateVisit(owner, historical.getId(), request("128.50"));
        service.updateVisit(owner, historical.getId(), request("0"));
        assertEquals(new BigDecimal("0.00"), records.selectById(historical.getId()).getAmount());
        assertTrue(linked(historical.getId()).isEmpty());
        var zero = service.createVisit(owner, request("0"));
        assertTrue(linked(zero.getId()).isEmpty());
    }

    @Test
    void shouldRejectAnotherUsersDetailUpdateAndDelete() {
        Long firstOwner = owner;
        var saved = service.createVisit(firstOwner, request("128.50"));
        createOwner();
        assertThrows(ResponseStatusException.class, () -> service.getVisit(owner, saved.getId()));
        assertThrows(ResponseStatusException.class, () -> service.updateVisit(owner, saved.getId(), request("100")));
        assertThrows(ResponseStatusException.class, () -> service.deleteVisit(owner, saved.getId()));
        assertEquals(new BigDecimal("128.50"), service.getVisit(firstOwner, saved.getId()).getAmount());
    }

    @Test
    void shouldRejectInvalidPrecisionNegativeAndOverflow() {
        for (String amount : new String[]{"-0.01", "1.001", "10000000000"}) {
            assertThrows(BusinessException.class, () -> service.createVisit(owner, request(amount)));
        }
        assertEquals(0L, records.selectCount(new QueryWrapper<HealthVisit>().eq("owner_user_id", owner)));
        var largest = service.createVisit(owner, request("9999999999.99"));
        assertEquals(new BigDecimal("9999999999.99"), largest.getAmount());
    }

    @Test
    void shouldRollbackSourceInsertAndEditWhenBillWriteFails() {
        jdbc.execute("ALTER TABLE gak_personal_bill ADD CONSTRAINT health_test_failure CHECK (merchant_name <> '回滚医院')");
        try {
            SaveHealthVisitRequest invalid = request("128.50");
            invalid.setHospitalName("回滚医院");
            assertThrows(DataIntegrityViolationException.class, () -> service.createVisit(owner, invalid));
            assertEquals(0L, records.selectCount(new QueryWrapper<HealthVisit>().eq("owner_user_id", owner)));
            var saved = service.createVisit(owner, request("128.50"));
            invalid.setAmount(new BigDecimal("100.00"));
            assertThrows(DataIntegrityViolationException.class, () -> service.updateVisit(owner, saved.getId(), invalid));
            assertEquals("测试医院", records.selectById(saved.getId()).getHospitalName());
            assertEquals(new BigDecimal("128.50"), records.selectById(saved.getId()).getAmount());
            assertEquals(new BigDecimal("128.50"), linked(saved.getId()).getFirst().getAmount());
        } finally {
            jdbc.execute("ALTER TABLE gak_personal_bill DROP CONSTRAINT health_test_failure");
        }
    }

    @Test
    void shouldDeleteOnlyOwnVisitBillAndUnlinkReports() {
        var first = service.createVisit(owner, request("128.50"));
        var second = service.createVisit(owner, request("50"));
        jdbc.update("INSERT INTO gak_health_report (id,owner_user_id,visit_id,exam_date,report_title,created_at,updated_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                first.getId() + 1, owner, first.getId(), LocalDate.of(2026, 10, 4), "测试报告");
        PersonalBill manual = new PersonalBill();
        manual.setOwnerUserId(owner);
        manual.setBillType("EXPENSE");
        manual.setCategoryName("医疗");
        manual.setCurrency("CNY");
        manual.setAmount(new BigDecimal("128.50"));
        manual.setBillDate(LocalDate.of(2026, 10, 4));
        manual.setCreatedAt(LocalDateTime.now());
        manual.setUpdatedAt(LocalDateTime.now());
        bills.insert(manual);
        service.deleteVisit(owner, first.getId());
        assertTrue(linked(first.getId()).isEmpty());
        assertEquals(1, linked(second.getId()).size());
        assertEquals(manual.getId(), bills.selectById(manual.getId()).getId());
        assertNull(jdbc.queryForObject("SELECT visit_id FROM gak_health_report WHERE id=?", Long.class, first.getId() + 1));
    }

    private List<PersonalBill> linked(Long visitId) {
        return bills.selectList(new QueryWrapper<PersonalBill>().eq("owner_user_id", owner)
                .eq("source_type", "HEALTH_VISIT").eq("source_id", visitId));
    }

    private SaveHealthVisitRequest request(String paid) {
        SaveHealthVisitRequest request = new SaveHealthVisitRequest();
        request.setVisitDate(LocalDate.of(2026, 10, 4));
        request.setHospitalName("测试医院");
        request.setDiagnosisSummary("诊断不应复制到个人账单");
        request.setAmount(paid == null ? null : new BigDecimal(paid));
        request.setAttachmentIds(List.of());
        return request;
    }

    @AfterAll
    static void removeIsolatedSchema() throws Exception {
        if (!schemaCreated || !SCHEMA.matches("health_test_[a-f0-9]{32}")) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            connection.createStatement().execute("DROP SCHEMA " + SCHEMA + " CASCADE");
        }
    }
}
