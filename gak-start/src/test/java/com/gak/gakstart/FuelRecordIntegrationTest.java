package com.gak.gakstart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.fuelstats.domain.FuelRecord;
import com.gak.fuelstats.dto.SaveFuelRecordRequest;
import com.gak.fuelstats.mapper.FuelRecordMapper;
import com.gak.fuelstats.service.FuelRecordService;
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

/** 显式开启后只在本机随机独立 schema 验证油耗账单事务，不初始化业务 schema。 */
@SpringBootTest(properties = {"gak.attachment.legacy-migration-enabled=false", "spring.sql.init.mode=always"})
@EnabledIfSystemProperty(named = "gak.fuel.db-test", matches = "true")
class FuelRecordIntegrationTest {
    private static final String SCHEMA = "fuel_test_" + UUID.randomUUID().toString().replace("-", "");
    private static String jdbcUrl;
    private static String username;
    private static String password;
    private static boolean schemaCreated;
    @Autowired private FuelRecordService service;
    @Autowired private FuelRecordMapper records;
    @Autowired private PersonalBillMapper bills;
    @Autowired private PersonalBillService billService;
    @Autowired private UserMapper users;
    @Autowired private JdbcTemplate jdbc;
    private Long owner;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = FuelRecordIntegrationTest.class.getResourceAsStream("/application.properties")) {
            properties.load(input);
        }
        StandardEnvironment environment = new StandardEnvironment();
        jdbcUrl = environment.resolvePlaceholders(properties.getProperty("spring.datasource.url"));
        username = environment.resolvePlaceholders(properties.getProperty("spring.datasource.username"));
        password = environment.resolvePlaceholders(properties.getProperty("spring.datasource.password"));
        if (!jdbcUrl.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/[^?]+")) {
            throw new IllegalStateException("油耗集成测试仅允许无额外参数的本机 PostgreSQL URL");
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            connection.createStatement().execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }
        String directory = Files.createTempDirectory("gak-fuel-test-").toString();
        registry.add("spring.datasource.url", () -> jdbcUrl + "?currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("gak.attachment.storage-root", () -> directory);
    }

    @BeforeEach
    void createOwner() {
        User user = new User();
        user.setUsername("fuel-test-" + UUID.randomUUID());
        user.setPasswordHash("test-not-a-login-password");
        user.setDisplayName("油耗集成测试");
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
    void shouldPersistCycleCostAndKeepBillIdentityOnUpdateAndDelete() {
        service.create(owner, request("1000", "50", "999", "FULL"));
        service.create(owner, request("1200", "10", "75.50", "PARTIAL"));
        var saved = service.create(owner, request("1500", "35", "249.50", "FULL"));
        assertEquals(new BigDecimal("0.650"), saved.getCostPerKm());
        assertEquals(new BigDecimal("325.00"), saved.getConsumptionAmount());
        PersonalBill bill = linked(saved.getId()).getFirst();
        assertEquals(new BigDecimal("249.50"), bill.getAmount());
        assertThrows(BusinessException.class, () -> billService.delete(owner, bill.getId()));
        SaveFuelRecordRequest edit = request("1500", "35", "224.50", "FULL");
        edit.setStationName("");
        var updated = service.update(owner, saved.getId(), edit);
        assertEquals(new BigDecimal("0.600"), updated.getCostPerKm());
        assertEquals(1, linked(saved.getId()).size());
        assertEquals(bill.getId(), linked(saved.getId()).getFirst().getId());
        assertEquals(new BigDecimal("224.50"), linked(saved.getId()).getFirst().getAmount());
        assertNull(linked(saved.getId()).getFirst().getMerchantName());
        assertNull(service.getById(owner, saved.getId()).getStationName());
        service.delete(owner, saved.getId());
        assertTrue(linked(saved.getId()).isEmpty());
        assertNull(records.selectById(saved.getId()));
    }

    @Test
    void shouldRollbackSourceInsertWhenBillConstraintRejectsWrite() {
        jdbc.execute("ALTER TABLE gak_personal_bill ADD CONSTRAINT fuel_test_failure CHECK (note <> '加油 · 回滚车辆')");
        SaveFuelRecordRequest request = request("1000", "50", "300", "FULL");
        request.setVehicleName("回滚车辆");
        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
                () -> service.create(owner, request));
        assertTrue(failure.getMessage().contains("fuel_test_failure"));
        assertEquals(0L, records.selectCount(new QueryWrapper<FuelRecord>().eq("owner_user_id", owner)));
        assertEquals(0L, bills.selectCount(new QueryWrapper<PersonalBill>().eq("owner_user_id", owner)));
    }

    @Test
    void shouldKeepUnlinkedHistoryUnbilledAndRejectOtherUsersDetail() {
        FuelRecord historical = new FuelRecord();
        BeanUtils.copyProperties(request("1000", "50", "300", "FULL"), historical);
        historical.setUnitPrice(new BigDecimal("6.000"));
        historical.setOwnerUserId(owner);
        historical.setCreatedAt(LocalDateTime.now());
        historical.setUpdatedAt(LocalDateTime.now());
        records.insert(historical);
        service.update(owner, historical.getId(), request("1000", "50", "250", "FULL"));
        assertTrue(linked(historical.getId()).isEmpty());
        Long firstOwner = owner;
        createOwner();
        assertThrows(ResponseStatusException.class, () -> service.getById(owner, historical.getId()));
        assertEquals(historical.getId(), service.getById(firstOwner, historical.getId()).getId());
    }

    private List<PersonalBill> linked(Long recordId) {
        return bills.selectList(new QueryWrapper<PersonalBill>().eq("owner_user_id", owner)
                .eq("source_type", "FUEL_RECORD").eq("source_id", recordId));
    }

    private SaveFuelRecordRequest request(String mileage, String volume, String paid, String fill) {
        SaveFuelRecordRequest request = new SaveFuelRecordRequest();
        request.setVehicleName("测试车辆");
        request.setFuelDate(LocalDate.of(2026, 10, 4));
        request.setOdometerKm(new BigDecimal(mileage));
        request.setFuelVolume(new BigDecimal(volume));
        request.setTotalAmount(new BigDecimal("1000"));
        request.setDiscountedAmount(new BigDecimal(paid));
        request.setFuelType("95");
        request.setFillType(fill);
        request.setStationName("测试油站");
        request.setAttachmentIds(List.of());
        return request;
    }

    @AfterAll
    static void removeIsolatedSchema() throws Exception {
        if (!schemaCreated || !SCHEMA.matches("fuel_test_[a-f0-9]{32}")) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            connection.createStatement().execute("DROP SCHEMA " + SCHEMA + " CASCADE");
        }
    }
}
