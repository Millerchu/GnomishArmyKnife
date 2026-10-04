package com.gak.gakstart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import com.gak.user.service.user.TokenService;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP 与真实 PostgreSQL 闭环；显式开启，所有数据只写入随机临时 schema。 */
@SpringBootTest(properties = {"gak.attachment.legacy-migration-enabled=false", "spring.sql.init.mode=always"})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "gak.growth.db-test", matches = "true")
class GrowthRecordIntegrationTest {
    private static final String SCHEMA = "growth_test_" + UUID.randomUUID().toString().replace("-", "");
    private static String jdbcUrl;
    private static String username;
    private static String password;
    @Autowired private MockMvc http;
    @Autowired private ObjectMapper json;
    @Autowired private TokenService tokens;
    @Autowired private UserMapper users;
    private String token;

    @DynamicPropertySource
    static void isolateDatabase(DynamicPropertyRegistry registry) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = GrowthRecordIntegrationTest.class.getResourceAsStream("/application.properties")) {
            properties.load(input);
        }
        StandardEnvironment environment = new StandardEnvironment();
        jdbcUrl = environment.resolvePlaceholders(properties.getProperty("spring.datasource.url"));
        username = environment.resolvePlaceholders(properties.getProperty("spring.datasource.username"));
        password = environment.resolvePlaceholders(properties.getProperty("spring.datasource.password"));
        if (!jdbcUrl.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/[^?]+")) {
            throw new IllegalStateException("芽记集成测试仅允许无附加参数的本机 PostgreSQL 地址");
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            connection.createStatement().execute("CREATE SCHEMA " + SCHEMA);
        }
        String directory = Files.createTempDirectory("gak-growth-test-").toString();
        registry.add("spring.datasource.url", () -> jdbcUrl + "?currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("gak.attachment.storage-root", () -> directory);
    }

    @BeforeEach
    void createOwner() {
        token = createToken("ADMIN");
    }

    private String createToken(String role) {
        User user = new User();
        user.setUsername("growth-test-" + UUID.randomUUID()); user.setDisplayName("芽记测试");
        user.setPasswordHash("test-not-a-real-password"); user.setRoleCode(role);
        user.setStatus("ENABLED"); user.setEnabled(true); user.setForceChangePassword(false);
        user.setCreatedAt(LocalDateTime.now()); user.setUpdatedAt(user.getCreatedAt());
        users.insert(user);
        return "Bearer " + tokens.issueToken(user.getId());
    }

    @Test
    void authenticationAppAuthorizationAndInputValidation() throws Exception {
        http.perform(get("/growth-records/children")).andExpect(status().isUnauthorized());
        http.perform(get("/growth-records/children").header("Authorization", createToken("USER")))
                .andExpect(status().isForbidden());
        send(post("/growth-records/children"), "{}").andExpect(status().isBadRequest());
        String child = createChild();
        send(post(path(child, "measurements")), "{\"measureDate\":\"2024-06-18\"}").andExpect(status().isBadRequest());
        send(post(path(child, "measurements")), "{\"measureDate\":\"2024-06-18\",\"heightCm\":-1}").andExpect(status().isBadRequest());
        send(post(path(child, "measurements")), "{\"measureDate\":\"2020-06-18\",\"heightCm\":50}").andExpect(status().isBadRequest());
    }

    @Test
    void realCrudNullClearingBirthBoundaryAndIsolation() throws Exception {
        String child = createChild();
        JsonNode first = body(send(post(path(child, "measurements")),
                "{\"measureDate\":\"2024-06-18\",\"heightCm\":99.6,\"weightKg\":15.2,\"recordedBy\":\"爸爸\"}")
                .andExpect(status().isOk()));
        String measurement = first.get("id").asText();
        assertTrue(first.get("id").isTextual());
        send(put(path(child, "measurements") + "/" + measurement),
                "{\"measureDate\":\"2024-06-18\",\"heightCm\":null,\"weightKg\":15.2,\"recordedBy\":null}")
                .andExpect(status().isOk());
        JsonNode persisted = body(send(get(path(child, "measurements")), null)).get(0);
        assertTrue(persisted.get("heightCm").isNull()); assertTrue(persisted.get("recordedBy").isNull());
        assertEquals(15.2, persisted.get("weightKg").asDouble());
        send(put("/growth-records/children/" + child),
                "{\"name\":\"宝宝\",\"birthday\":\"2025-01-01\",\"gender\":\"UNKNOWN\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GROWTH_BIRTHDAY_CONFLICT"));
        String ownToken = token;
        token = createToken("ADMIN");
        send(get(path(child, "measurements")), null).andExpect(status().isBadRequest());
        send(delete(path(child, "measurements") + "/" + measurement), null).andExpect(status().isBadRequest());
        assertEquals(0, body(send(get("/growth-records/children"), null)).size());
        token = ownToken;
        send(delete("/growth-records/children/" + child), null).andExpect(status().isBadRequest());
        send(delete(path(child, "measurements") + "/" + measurement), null).andExpect(status().isOk());
        send(delete("/growth-records/children/" + child), null).andExpect(status().isOk());
    }

    @Test
    void plannedVaccinationBecomesCompletedAndMilestoneCrud() throws Exception {
        String child = createChild();
        String vaccination = body(send(post(path(child, "vaccinations")),
                "{\"vaccineName\":\"预约疫苗\",\"dose\":1,\"vaccinationDate\":\"2099-01-01\",\"status\":\"PLANNED\"}")
                .andExpect(status().isOk())).get("id").asText();
        send(put(path(child, "vaccinations") + "/" + vaccination),
                "{\"vaccineName\":\"预约疫苗\",\"dose\":1,\"vaccinationDate\":\"2024-06-18\",\"status\":\"COMPLETED\"}")
                .andExpect(status().isOk());
        assertEquals("COMPLETED", body(send(get(path(child, "vaccinations")), null)).get(0).get("status").asText());
        String milestone = body(send(post(path(child, "milestones")),
                "{\"title\":\"第一次走路\",\"eventDate\":\"2022-06-18\",\"note\":\"走了五步\"}")
                .andExpect(status().isOk())).get("id").asText();
        send(put(path(child, "milestones") + "/" + milestone),
                "{\"title\":\"第一次走路\",\"eventDate\":\"2022-06-18\",\"note\":null}").andExpect(status().isOk());
        assertTrue(body(send(get(path(child, "milestones")), null)).get(0).get("note").isNull());
        send(delete(path(child, "milestones") + "/" + milestone), null).andExpect(status().isOk());
        send(delete(path(child, "vaccinations") + "/" + vaccination), null).andExpect(status().isOk());
    }

    private String createChild() throws Exception {
        return body(send(post("/growth-records/children"),
                "{\"name\":\"小果冻\",\"birthday\":\"2021-06-18\",\"gender\":\"UNKNOWN\"}")
                .andExpect(status().isOk())).get("id").asText();
    }

    private String path(String child, String type) {
        return "/growth-records/children/" + child + "/" + type;
    }

    private org.springframework.test.web.servlet.ResultActions send(MockHttpServletRequestBuilder request, String payload) throws Exception {
        request.header("Authorization", token);
        if (payload != null) request.contentType("application/json").content(payload);
        return http.perform(request);
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString()).get("data");
    }

    @AfterAll
    static void cleanupSchema() throws Exception {
        if (jdbcUrl != null) {
            try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
                connection.createStatement().execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            }
        }
    }
}
