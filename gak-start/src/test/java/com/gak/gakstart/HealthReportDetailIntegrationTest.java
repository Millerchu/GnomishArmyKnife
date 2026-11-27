package com.gak.gakstart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gak.framework.exception.BusinessException;
import com.gak.healthrecord.dto.HealthRecordQueryRequest;
import com.gak.healthrecord.dto.HealthReportDetailItemRequest;
import com.gak.healthrecord.dto.HealthReportQueryRequest;
import com.gak.healthrecord.dto.SaveHealthRecordRequest;
import com.gak.healthrecord.dto.SaveHealthReportDetailsRequest;
import com.gak.healthrecord.dto.SaveHealthReportRequest;
import com.gak.healthrecord.mapper.HealthReportDetailMapper;
import com.gak.healthrecord.service.HealthRecordService;
import com.gak.healthrecord.service.HealthReportDetailService;
import com.gak.healthrecord.vo.HealthRecordVO;
import com.gak.healthrecord.vo.HealthReportDetailsVO;
import com.gak.healthrecord.vo.HealthReportVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

/**
 * 显式开启后在隔离 PostgreSQL schema 验证体检报告明细、一对一关联、并发版本与升级脚本幂等性。
 */
@SpringBootTest(properties = {"gak.attachment.legacy-migration-enabled=false", "spring.sql.init.mode=always"})
@EnabledIfSystemProperty(named = "gak.health.db-test", matches = "true")
class HealthReportDetailIntegrationTest {

    private static final String SCHEMA = "health_detail_test_" + UUID.randomUUID().toString().replace("-", "");
    private static String jdbcUrl;
    private static String username;
    private static String password;
    private static boolean schemaCreated;

    @Autowired
    private HealthRecordService healthRecordService;

    @Autowired
    private HealthReportDetailService healthReportDetailService;

    @Autowired
    private HealthReportDetailMapper healthReportDetailMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long owner;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = HealthReportDetailIntegrationTest.class.getResourceAsStream("/application.properties")) {
            properties.load(input);
        }
        StandardEnvironment environment = new StandardEnvironment();
        jdbcUrl = environment.resolvePlaceholders(properties.getProperty("spring.datasource.url"));
        username = environment.resolvePlaceholders(properties.getProperty("spring.datasource.username"));
        password = environment.resolvePlaceholders(properties.getProperty("spring.datasource.password"));
        if (!jdbcUrl.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/[^?]+")) {
            throw new IllegalStateException("健康明细集成测试仅允许无额外参数的本机 PostgreSQL URL");
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            connection.createStatement().execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }
        String directory = Files.createTempDirectory("gak-health-detail-test-").toString();
        registry.add("spring.datasource.url", () -> jdbcUrl + "?currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("gak.attachment.storage-root", () -> directory);
    }

    @BeforeEach
    void createOwner() {
        owner = insertUser();
    }

    @Test
    void shouldReapplyReportDetailsUpgradeSqlTwiceIdempotently() throws Exception {
        HealthReportVO report = createReport(owner, LocalDate.of(2026, 5, 10), "存量体检报告");
        String schema;
        try (InputStream input = getClass().getResourceAsStream("/schema.sql")) {
            schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String upgrade = schema.substring(schema.indexOf("-- 2026-11-27 体检报告明细"));
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ByteArrayResource(upgrade.getBytes(StandardCharsets.UTF_8)));
        populator.execute(jdbcTemplate.getDataSource());
        populator.execute(jdbcTemplate.getDataSource());

        HealthReportDetailsVO details = healthReportDetailService.getDetails(owner, report.getId());
        assertEquals(0, details.getRevision());
        assertNull(details.getLinkedRecordId());
        assertTrue(details.getItems().isEmpty());
    }

    @Test
    void shouldPersistJsonbDetailsDeriveNumericAndEnforceRevisionAndOneToOneLinking() {
        HealthRecordVO rec1 = createRecord(owner, LocalDate.of(2026, 5, 10), "176.0", "78.0");
        assertEquals(new BigDecimal("25.18"), rec1.getBmi());
        HealthReportVO rep1 = createReport(owner, LocalDate.of(2026, 5, 10), "体检报告A");
        HealthReportVO rep2 = createReport(owner, LocalDate.of(2026, 5, 10), "体检报告B");

        HealthReportDetailItemRequest item1 = new HealthReportDetailItemRequest();
        item1.setGroup("URINALYSIS");
        item1.setItemCode("urineSpecificGravity");
        item1.setName("尿比重");
        item1.setResultType("NUMBER");
        item1.setResultText("1.012");
        item1.setReferenceText("1.015–1.025");
        item1.setAbnormalFlag("LOW");
        item1.setSourcePage(3);

        HealthReportDetailItemRequest item2 = new HealthReportDetailItemRequest();
        item2.setGroup("URINALYSIS");
        item2.setItemCode("urineProtein");
        item2.setName("尿蛋白");
        item2.setResultType("QUALITATIVE");
        item2.setResultText("阴性");

        HealthReportDetailItemRequest item3 = new HealthReportDetailItemRequest();
        item3.setGroup("IMAGING");
        item3.setItemCode("renalCyst");
        item3.setName("右肾囊肿");
        item3.setResultType("TEXT");
        item3.setResultText("<0.05");

        SaveHealthReportDetailsRequest save1 = buildSaveRequest(0, rec1.getId(), List.of(item1, item2, item3));
        HealthReportDetailsVO saved = healthReportDetailService.saveDetails(owner, rep1.getId(), save1);
        assertEquals(1, saved.getRevision());
        assertEquals(rec1.getId(), saved.getLinkedRecordId());
        assertEquals("1.012", saved.getItems().get(0).getNumericValue());
        assertNull(saved.getItems().get(1).getNumericValue());
        assertEquals("<0.05", saved.getItems().get(2).getResultText());
        assertEquals("array", jdbcTemplate.queryForObject(
                "SELECT jsonb_typeof(items) FROM gak_health_report_detail WHERE report_id = ?",
                String.class, rep1.getId()));

        // 双向查询
        HealthRecordVO loadedRec = healthRecordService.getRecord(owner, rec1.getId());
        assertEquals(rep1.getId(), loadedRec.getSourceReportId());
        HealthReportQueryRequest repQuery = new HealthReportQueryRequest();
        HealthReportVO listedRep = healthRecordService.pageReports(owner, repQuery).list().stream()
                .filter(item -> item.getId().equals(rep1.getId())).findFirst().orElseThrow();
        assertEquals(rec1.getId(), listedRep.getLinkedRecordId());

        // 过期版本冲突
        SaveHealthReportDetailsRequest stale = buildSaveRequest(0, rec1.getId(), List.of());
        BusinessException conflict = assertThrows(BusinessException.class,
                () -> healthReportDetailService.saveDetails(owner, rep1.getId(), stale));
        assertEquals("HEALTH_REPORT_DETAIL_CONFLICT", conflict.getCode());

        // 另一份报告重复关联同一记录
        SaveHealthReportDetailsRequest dupLink = buildSaveRequest(0, rec1.getId(), List.of());
        BusinessException alreadyLinked = assertThrows(BusinessException.class,
                () -> healthReportDetailService.saveDetails(owner, rep2.getId(), dupLink));
        assertEquals("HEALTH_RECORD_ALREADY_LINKED", alreadyLinked.getCode());

        // 删除已关联核心指标被拒绝
        BusinessException deleteBlocked = assertThrows(BusinessException.class,
                () -> healthRecordService.deleteRecord(owner, rec1.getId()));
        assertEquals("HEALTH_RECORD_LINKED", deleteBlocked.getCode());

        // 清空明细并解除关联保留版本行，版本增至 2
        SaveHealthReportDetailsRequest clearReq = buildSaveRequest(1, null, List.of());
        HealthReportDetailsVO cleared = healthReportDetailService.saveDetails(owner, rep1.getId(), clearReq);
        assertEquals(2, cleared.getRevision());
        assertNull(cleared.getLinkedRecordId());
        assertTrue(cleared.getItems().isEmpty());
        assertNotNull(healthReportDetailMapper.findByReportId(rep1.getId()));

        // 解除关联后核心指标可删除
        healthRecordService.deleteRecord(owner, rec1.getId());
        assertThrows(ResponseStatusException.class, () -> healthRecordService.getRecord(owner, rec1.getId()));

        // 删除报告级联删除明细行
        HealthRecordVO rec2 = createRecord(owner, LocalDate.of(2026, 5, 11), "176.0", "77.0");
        healthReportDetailService.saveDetails(owner, rep2.getId(), buildSaveRequest(0, rec2.getId(), List.of(item1)));
        healthRecordService.deleteReport(owner, rep2.getId());
        assertNull(healthReportDetailMapper.findByReportId(rep2.getId()));
        assertNull(healthRecordService.getRecord(owner, rec2.getId()).getSourceReportId());
    }

    @Test
    void shouldFilterRecordsByMeasureDateAndEnforceCrossAccountIsolation() {
        HealthRecordVO day1 = createRecord(owner, LocalDate.of(2026, 5, 1), "176.0", "76.0");
        createRecord(owner, LocalDate.of(2026, 5, 2), "176.0", "75.5");
        HealthReportVO report = createReport(owner, LocalDate.of(2026, 5, 1), "跨账号隔离测试报告");

        HealthRecordQueryRequest query = new HealthRecordQueryRequest();
        query.setMeasureDate(LocalDate.of(2026, 5, 1));
        var page = healthRecordService.pageRecords(owner, query);
        assertEquals(1L, page.total());
        assertEquals(day1.getId(), page.list().get(0).getId());

        Long otherUser = insertUser();
        assertThrows(ResponseStatusException.class, () -> healthRecordService.getRecord(otherUser, day1.getId()));
        assertThrows(ResponseStatusException.class, () -> healthReportDetailService.getDetails(otherUser, report.getId()));
        HealthReportVO otherReport = createReport(otherUser, LocalDate.of(2026, 5, 1), "他人报告");
        assertThrows(ResponseStatusException.class, () -> healthReportDetailService.saveDetails(
                otherUser, otherReport.getId(), buildSaveRequest(0, day1.getId(), List.of())));
    }

    private Long insertUser() {
        User user = new User();
        user.setUsername("health-detail-" + UUID.randomUUID());
        user.setPasswordHash("test-not-a-login-password");
        user.setDisplayName("健康明细测试");
        user.setRoleCode("USER");
        user.setStatus("ENABLED");
        user.setEnabled(true);
        user.setForceChangePassword(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user.getId();
    }

    private HealthRecordVO createRecord(Long userId, LocalDate date, String heightCm, String weightKg) {
        SaveHealthRecordRequest request = new SaveHealthRecordRequest();
        request.setMeasureDate(date);
        request.setHeightCm(new BigDecimal(heightCm));
        request.setWeightKg(new BigDecimal(weightKg));
        return healthRecordService.createRecord(userId, request);
    }

    private HealthReportVO createReport(Long userId, LocalDate date, String title) {
        SaveHealthReportRequest request = new SaveHealthReportRequest();
        request.setExamDate(date);
        request.setReportTitle(title);
        request.setHospitalName("合成体检中心");
        request.setAttachmentIds(List.of());
        return healthRecordService.createReport(userId, request);
    }

    private SaveHealthReportDetailsRequest buildSaveRequest(int expectedRevision, Long linkedRecordId,
                                                            List<HealthReportDetailItemRequest> items) {
        SaveHealthReportDetailsRequest request = new SaveHealthReportDetailsRequest();
        request.setExpectedRevision(expectedRevision);
        request.setLinkedRecordId(linkedRecordId);
        request.setItems(items);
        return request;
    }

    @AfterAll
    static void removeIsolatedSchema() throws Exception {
        if (!schemaCreated || !SCHEMA.matches("health_detail_test_[a-f0-9]{32}")) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            connection.createStatement().execute("DROP SCHEMA " + SCHEMA + " CASCADE");
        }
    }
}
