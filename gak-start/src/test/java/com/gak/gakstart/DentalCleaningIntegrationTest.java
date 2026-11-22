package com.gak.gakstart;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.gak.healthrecord.service.DentalCleaningService;
import com.gak.healthrecord.service.DentalReminderJob;
import com.gak.user.service.user.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 在显式指定的临时数据库中贯通洁牙 API、调度和消息收件箱。 */
@SpringBootTest(properties = {
        "spring.datasource.url=${gak.dental.test.url}",
        "gak.attachment.legacy-migration-enabled=false"
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "gak.dental.test.url", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/gak_dental_test")
class DentalCleaningIntegrationTest {
    private static final String PATH = "/health-records/dental-cleanings";
    private final long owner = IdWorker.getId();
    private final long other = IdWorker.getId();
    private final long unauthorizedUser = IdWorker.getId();

    @Autowired private MockMvc http;
    @Autowired private TokenService tokens;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DentalReminderJob job;
    @Autowired private DentalCleaningService service;

    @Test
    void shouldPersistPersonalRecordsAndDeliverOnlyOneMessagePerCycle() throws Exception {
        createUser(owner, true);
        createUser(other, true);
        createUser(unauthorizedUser, false);

        http.perform(get(PATH)).andExpect(status().isUnauthorized());
        http.perform(as(unauthorizedUser, get(PATH))).andExpect(status().isForbidden());
        http.perform(as(owner, get(PATH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reminder.enabled").value(true))
                .andExpect(jsonPath("$.data.reminder.intervalMonths").value(12))
                .andExpect(jsonPath("$.data.reminder.leadDays").value(7));

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate cleaning = today.plusDays(3).minusYears(1);
        LocalDate due = cleaning.plusYears(1);
        String body = """
                {"cleaningDate":"%s","hospitalName":"测试口腔医院","amount":128.50,"note":"复查"}
                """.formatted(cleaning);
        http.perform(as(owner, post(PATH)).contentType("application/json").content(body)).andExpect(status().isOk());

        Long id = jdbc.queryForObject("SELECT id FROM gak_dental_cleaning WHERE owner_user_id = ?", Long.class, owner);
        http.perform(as(owner, get(PATH)))
                .andExpect(jsonPath("$.data.records[0].id").value(id.toString()))
                .andExpect(jsonPath("$.data.records[0].amount").value(128.5))
                .andExpect(jsonPath("$.data.reminder.nextDueDate").value(due.toString()));
        http.perform(as(other, get(PATH))).andExpect(jsonPath("$.data.records").isEmpty());
        http.perform(as(other, put(PATH + "/" + id)).contentType("application/json").content(body))
                .andExpect(status().isNotFound());
        http.perform(as(other, delete(PATH + "/" + id))).andExpect(status().isNotFound());
        http.perform(as(owner, put(PATH + "/" + id)).contentType("application/json").content(body))
                .andExpect(status().isOk());

        saveReminder(false, 12, 7);
        job.sendDueReminders();
        http.perform(as(owner, get("/messages/inbox"))).andExpect(jsonPath("$.data.total").value(0));

        saveReminder(true, 12, 7);
        job.sendDueReminders();
        job.sendDueReminders();
        http.perform(as(owner, get("/messages/inbox")))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].title").value("洁牙提醒"));
        http.perform(as(other, get("/messages/inbox"))).andExpect(jsonPath("$.data.total").value(0));

        service.sendIfDue(owner, due.plusYears(1).minusDays(7));
        http.perform(as(owner, get("/messages/inbox"))).andExpect(jsonPath("$.data.total").value(2));

        saveReminder(true, 18, 14);
        http.perform(as(owner, get(PATH)))
                .andExpect(jsonPath("$.data.reminder.nextDueDate").value(cleaning.plusMonths(18).toString()))
                .andExpect(jsonPath("$.data.reminder.nextReminderDate").value(cleaning.plusMonths(18).minusDays(14).toString()));
        http.perform(as(owner, delete(PATH + "/" + id))).andExpect(status().isOk());
        http.perform(as(owner, get(PATH)))
                .andExpect(jsonPath("$.data.records").isEmpty())
                .andExpect(jsonPath("$.data.reminder.nextDueDate").doesNotExist());
    }

    private void createUser(long id, boolean granted) {
        jdbc.update("""
                INSERT INTO gak_user (id, username, display_name, password_hash, created_at, updated_at)
                VALUES (?, ?, '洁牙测试用户', 'test', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """, id, "dental-test-" + id);
        if (granted) {
            jdbc.update("""
                    INSERT INTO gak_user_app_permission (id, user_id, app_id, app_code, granted, created_at, updated_at)
                    SELECT ?, ?, id, app_code, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    FROM gak_system_app WHERE app_code = 'APP_HEALTH_RECORD'
                    """, IdWorker.getId(), id);
        }
    }

    private MockHttpServletRequestBuilder as(long user, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + tokens.issueToken(user));
    }

    private void saveReminder(boolean enabled, int interval, int lead) throws Exception {
        http.perform(as(owner, put(PATH + "/reminder")).contentType("application/json").content("""
                {"enabled":%s,"intervalMonths":%s,"leadDays":%s}
                """.formatted(enabled, interval, lead)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(enabled))
                .andExpect(jsonPath("$.data.intervalMonths").value(interval))
                .andExpect(jsonPath("$.data.leadDays").value(lead));
    }
}
