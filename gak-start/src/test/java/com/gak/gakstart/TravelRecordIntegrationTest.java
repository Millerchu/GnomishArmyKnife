package com.gak.gakstart;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.framework.exception.BusinessException;
import com.gak.personalbills.domain.PersonalBill;
import com.gak.personalbills.mapper.PersonalBillMapper;
import com.gak.travelrecord.domain.TravelRecord;
import com.gak.travelrecord.dto.*;
import com.gak.travelrecord.mapper.TravelRecordMapper;
import com.gak.travelrecord.service.*;
import com.gak.travelrecord.vo.TravelRecordVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import com.gak.attachment.service.AttachmentService;
import com.gak.datamigration.handler.TravelRecordMigrationHandler;
import com.gak.datamigration.handler.MigrationResourceHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import com.gak.user.service.user.TokenService;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 显式开启后仅在 localhost 的随机独立 schema 中运行，绝不初始化已有业务 schema。 */
@SpringBootTest(properties = {"gak.attachment.legacy-migration-enabled=false", "spring.sql.init.mode=always"})
@EnabledIfSystemProperty(named = "gak.travel.db-test", matches = "true")
@AutoConfigureMockMvc
class TravelRecordIntegrationTest {
    private static final String SCHEMA = "travel_test_" + UUID.randomUUID().toString().replace("-", "");
    private static String jdbcUrl;
    private static String username;
    private static String password;
    @Autowired private TravelRecordService service;
    @Autowired private TravelGroupService groups;
    @Autowired private TravelRecordMapper records;
    @Autowired private PersonalBillMapper bills;
    @Autowired private UserMapper users;
    @Autowired private TravelAttachmentAccessPolicy attachmentPolicy;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private AttachmentService attachmentService;
    @Autowired private TravelRecordMigrationHandler migration;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MockMvc http;
    @Autowired private TokenService tokens;
    @Autowired private javax.sql.DataSource dataSource;
    private Long owner;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = TravelRecordIntegrationTest.class.getResourceAsStream("/application.properties")) {
            properties.load(input);
        }
        StandardEnvironment environment = new StandardEnvironment();
        jdbcUrl = environment.resolvePlaceholders(properties.getProperty("spring.datasource.url"));
        username = environment.resolvePlaceholders(properties.getProperty("spring.datasource.username"));
        password = environment.resolvePlaceholders(properties.getProperty("spring.datasource.password"));
        if (!jdbcUrl.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/[^?]+")) {
            throw new IllegalStateException("集成测试仅允许无额外参数的本机 PostgreSQL URL");
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            connection.createStatement().execute("CREATE SCHEMA " + SCHEMA);
        }
        String directory = Files.createTempDirectory("gak-travel-test-").toString();
        registry.add("spring.datasource.url", () -> jdbcUrl + "?currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("gak.attachment.storage-root", () -> directory);
    }

    @BeforeEach
    void createOwner() {
        User user = new User();
        user.setUsername("travel-test-" + UUID.randomUUID());
        user.setPasswordHash("test-not-a-login-password");
        user.setDisplayName("行程集成测试");
        user.setRoleCode("USER"); user.setStatus("ENABLED"); user.setEnabled(true);
        user.setForceChangePassword(false);
        user.setCreatedAt(LocalDateTime.now()); user.setUpdatedAt(LocalDateTime.now());
        users.insert(user); owner = user.getId();
    }

    @Test
    void saveRetryRefundDetachRelinkAndDeleteDoNotDuplicateBills() {
        SaveTravelRecordRequest request = request();
        request.setPaidAmount(new BigDecimal("500.00"));
        TravelRecordVO created = service.create(owner, request);
        assertEquals(created.getId(), service.create(owner, request).getId());
        assertEquals(1, linked(created.getId()).size());
        SaveTravelRecordRequest edit = edit(created);
        edit.setRefundAmount(new BigDecimal("80.00")); edit.setRefundDate(LocalDate.of(2014, 1, 4));
        TravelRecordVO refunded = service.update(owner, created.getId(), edit);
        assertEquals(new BigDecimal("420.00"), linked(created.getId()).stream().map(PersonalBill::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        Long paymentId = linked(created.getId()).stream().filter(item -> "PAYMENT".equals(item.getSourceEntry())).findFirst().orElseThrow().getId();
        edit = edit(refunded); edit.setSyncBill(false);
        TravelRecordVO detached = service.update(owner, created.getId(), edit);
        assertTrue(linked(created.getId()).isEmpty());
        assertNull(bills.selectById(paymentId).getSourceType());
        edit = edit(detached); edit.setSyncBill(true); edit.setExistingBillId(paymentId);
        TravelRecordVO relinked = service.update(owner, created.getId(), edit);
        assertEquals(2, linked(created.getId()).size());
        assertEquals(2L, bills.selectCount(new QueryWrapper<PersonalBill>().eq("owner_user_id", owner)));
        service.delete(owner, relinked.getId(), true);
        assertEquals(0L, bills.selectCount(new QueryWrapper<PersonalBill>().eq("owner_user_id", owner)));
    }

    @Test
    void attachmentFailureRollsBackRecordAndBill() {
        SaveTravelRecordRequest request = request(); request.setPaidAmount(new BigDecimal("10.00"));
        request.setAttachmentIds(List.of(-999L));
        assertThrows(RuntimeException.class, () -> service.create(owner, request));
        assertEquals(0L, records.selectCount(new QueryWrapper<TravelRecord>().eq("owner_user_id", owner)));
        assertEquals(0L, bills.selectCount(new QueryWrapper<PersonalBill>().eq("owner_user_id", owner)));
    }

    @Test
    void incompleteHistoricalRecordNullClearingAndStaleVersion() {
        SaveTravelRecordRequest request = request(); request.setSeat("12A");
        TravelRecordVO first = service.create(owner, request);
        SaveTravelRecordRequest update = edit(first); update.setSeat(null);
        TravelRecordVO second = service.update(owner, first.getId(), update);
        assertNull(second.getSeat()); assertNull(second.getPaidAmount());
        assertEquals(0, second.getBills().size());
        assertThrows(BusinessException.class, () -> service.update(owner, first.getId(), update));
        assertEquals(1L, service.page(owner, new TravelRecordQueryRequest()).total());
        assertThrows(RuntimeException.class, () -> service.detail(-7L, first.getId()));
        assertFalse(attachmentPolicy.canView(-7L, first.getId()));
    }

    @Test
    void groupDeletionAndChangeLinkKeepRecordsAndVersionThem() {
        SaveTravelGroupRequest group = new SaveTravelGroupRequest(); group.setName("旧旅行");
        Long groupId = groups.save(owner, null, group).getId();
        SaveTravelRecordRequest request = request(); request.setGroupId(groupId);
        TravelRecordVO original = service.create(owner, request);
        SaveTravelRecordRequest changed = request(); changed.setDepartureDate(LocalDate.of(2014, 1, 7));
        changed.setPreviousRecordId(original.getId()); changed.setGroupId(groupId);
        TravelRecordVO replacement = service.create(owner, changed);
        assertEquals("CHANGED", service.detail(owner, original.getId()).getStatus());
        groups.delete(owner, groupId);
        assertNull(service.detail(owner, original.getId()).getGroupId());
        assertNull(service.detail(owner, replacement.getId()).getGroupId());
        service.delete(owner, original.getId(), false);
        assertNull(service.detail(owner, replacement.getId()).getPreviousRecordId());
    }

    @Test
    void outerTransactionRollsBackBothWrites() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        SaveTravelRecordRequest request = request(); request.setPaidAmount(new BigDecimal("15.00"));
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            service.create(owner, request); throw new IllegalStateException("rollback");
        }));
        assertEquals(0L, records.selectCount(new QueryWrapper<TravelRecord>().eq("owner_user_id", owner)));
        assertEquals(0L, bills.selectCount(new QueryWrapper<PersonalBill>().eq("owner_user_id", owner)));
    }

    @Test
    void backupRestoresTicketBytesBillsAndStableRelations() throws Exception {
        users.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<User>()
                .eq("id", owner).set("role_code", "ADMIN"));
        var image = new java.awt.image.BufferedImage(20, 20, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var buffer = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", buffer);
        byte[] ticket = buffer.toByteArray();
        var uploaded = attachmentService.uploadBytes(owner, ticket, "ticket.png", "ATTACHMENT");
        SaveTravelRecordRequest request = request(); request.setPaidAmount(new BigDecimal("38.00"));
        request.setAttachmentIds(List.of(uploaded.getId()));
        TravelRecordVO saved = service.create(owner, request);
        var bundle = migration.exportData(new MigrationResourceHandler.ExportContext(true));
        var directory = Files.createTempDirectory("gak-travel-backup-test-").toAbsolutePath();
        var dataFile = directory.resolve(bundle.entryPath());
        Files.createDirectories(dataFile.getParent());
        objectMapper.writeValue(dataFile.toFile(), bundle.payload());
        for (var file : bundle.attachments()) {
            var target = directory.resolve(file.entryPath());
            Files.createDirectories(target.getParent()); Files.copy(file.sourcePath(), target);
        }
        service.delete(owner, saved.getId(), true);
        var context = new MigrationResourceHandler.ImportContext("MERGE", true, directory);
        migration.importData(context);
        var restored = service.detail(owner, saved.getId());
        assertEquals(1, restored.getBills().size()); assertEquals(1, restored.getAttachments().size());
        var attachment = restored.getAttachments().getFirst();
        try (var input = attachmentService.loadContent(owner, attachment.getId(), false).resource().getInputStream()) {
            assertArrayEquals(ticket, input.readAllBytes());
        }
        assertThrows(RuntimeException.class, () -> attachmentService.loadContent(-7L, attachment.getId(), false));
        migration.importData(context);
        assertEquals(1, service.detail(owner, saved.getId()).getBills().size());
        assertEquals(1, service.detail(owner, saved.getId()).getAttachments().size());
    }

    @Test
    void apiRequiresAppPermissionAndValidatesPayload() throws Exception {
        http.perform(get("/travel-records")).andExpect(status().isUnauthorized());
        String token = "Bearer " + tokens.issueToken(owner);
        http.perform(get("/travel-records").header("Authorization", token)).andExpect(status().isForbidden());
        users.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<User>()
                .eq("id", owner).set("role_code", "ADMIN"));
        http.perform(get("/travel-records").header("Authorization", token)).andExpect(status().isOk());
        http.perform(post("/travel-records").header("Authorization", token).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        http.perform(post("/travel-records").header("Authorization", token).contentType("application/json")
                .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.departureDate").value("2014-01-05"));
    }

    @Test
    void concurrentCreateRetryProducesOneRecordAndOneBill() throws Exception {
        SaveTravelRecordRequest request = request(); request.setPaidAmount(new BigDecimal("20.00"));
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.create(owner, request));
            var second = executor.submit(() -> service.create(owner, request));
            assertEquals(first.get().getId(), second.get().getId());
        }
        assertEquals(1L, records.selectCount(new QueryWrapper<TravelRecord>().eq("owner_user_id", owner)));
        assertEquals(1L, bills.selectCount(new QueryWrapper<PersonalBill>().eq("owner_user_id", owner)));
    }

    @Test
    void incrementalUpgradeCanRunTwiceWithoutRemovingRecords() throws Exception {
        service.create(owner, request());
        String sql = Files.readString(java.nio.file.Path.of("../docs/sql/20261004-personal-travel.sql"));
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql); statement.execute(sql);
        }
        assertEquals(1L, records.selectCount(new QueryWrapper<TravelRecord>().eq("owner_user_id", owner)));
    }

    @Test
    void deletedDetachedBillCanBeRecreatedAndRetainedBillCannotBeClaimedByAnotherTrip() {
        SaveTravelRecordRequest request = request(); request.setPaidAmount(new BigDecimal("50.00"));
        TravelRecordVO saved = service.create(owner, request);
        Long billId = saved.getBills().getFirst().getId();
        SaveTravelRecordRequest disable = edit(saved); disable.setSyncBill(false);
        TravelRecordVO detached = service.update(owner, saved.getId(), disable);
        SaveTravelRecordRequest other = request(); other.setPaidAmount(new BigDecimal("50.00"));
        other.setExistingBillId(billId); other.setDuplicateConfirmed(true);
        assertThrows(BusinessException.class, () -> service.create(owner, other));
        bills.deleteById(billId);
        SaveTravelRecordRequest enable = edit(detached); enable.setSyncBill(true);
        TravelRecordVO recreated = service.update(owner, saved.getId(), enable);
        assertEquals(1, recreated.getBills().size());
        assertNotEquals(billId, recreated.getBills().getFirst().getId());
    }

    private List<PersonalBill> linked(Long id) {
        return bills.selectList(new QueryWrapper<PersonalBill>().eq("owner_user_id", owner).eq("source_id", id));
    }
    private SaveTravelRecordRequest edit(TravelRecordVO record) {
        SaveTravelRecordRequest request = new SaveTravelRecordRequest(); BeanUtils.copyProperties(record, request);
        request.setAttachmentIds(List.of()); return request;
    }
    private SaveTravelRecordRequest request() {
        SaveTravelRecordRequest request = new SaveTravelRecordRequest();
        request.setRequestKey(UUID.randomUUID().toString()); request.setDeparture("南京南"); request.setArrival("上海虹桥");
        request.setTransportType("TRAIN"); request.setStatus("COMPLETED");
        request.setDepartureDate(LocalDate.of(2014, 1, 5)); request.setPaymentDate(LocalDate.of(2014, 1, 1));
        request.setDepartureZone("Asia/Shanghai"); request.setArrivalZone("Asia/Shanghai");
        request.setCurrency("CNY"); request.setSyncBill(true); request.setUseDepartureDate(false);
        request.setAttachmentIds(List.of()); return request;
    }

    @AfterAll
    static void removeIsolatedSchema() throws Exception {
        if (jdbcUrl == null || !SCHEMA.matches("travel_test_[a-f0-9]{32}")) { return; }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            connection.createStatement().execute("DROP SCHEMA " + SCHEMA + " CASCADE");
        }
    }
}
