package com.gak.gakstart;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.gak.travelrecord.domain.TravelRecord;
import com.gak.travelrecord.service.TravelBillSyncService;
import com.gak.user.service.user.LoginCaptcha;
import com.gak.user.service.user.LoginCaptchaService;
import com.gak.user.service.user.PasswordCryptoService;
import com.gak.user.service.user.TokenService;
import com.gak.wowcharacter.service.WowCharacterService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.util.Base64;
import javax.crypto.Cipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 在专用本地 PostgreSQL 库验证 P1 的 HTTP 权限和 SQL 写入。 */
@SpringBootTest(properties = {
        "spring.datasource.url=${gak.p1.test.url}",
        "spring.sql.init.mode=always",
        "gak.attachment.legacy-migration-enabled=false"
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "gak.p1.test.url", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/gak_p1_review")
class P1EndpointIntegrationTest {
    @Autowired private MockMvc http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TokenService tokens;
    @Autowired private PasswordEncoder passwords;
    @Autowired private PasswordCryptoService crypto;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private TravelBillSyncService travelBills;
    @Autowired private WowCharacterService wow;

    @Test
    void appGrantsAndRecordingDetailsAreEnforced() throws Exception {
        long owner = user(false);
        long other = user(false);
        long take = IdWorker.getId();
        jdbc.update("""
                INSERT INTO gak_instrument_practice_take
                    (id,owner_user_id,instrument_id,tuning_id,bpm,meter,duration_ms,events_json,event_count,created_at,updated_at)
                VALUES (?,?,'guzheng','d-pentatonic',90,'4/4',1000,'[{"type":"damp"}]',1,now(),now())
                """, take, owner);
        String path = "/instrument-practice/takes";
        String ownerToken = bearer(owner);
        http.perform(get("/fuel-records/latest-prices")).andExpect(status().isUnauthorized());
        http.perform(get(path).header("Authorization", ownerToken)).andExpect(status().isForbidden());
        grant(owner, "APP_INSTRUMENT_PRACTICE");
        grant(other, "APP_INSTRUMENT_PRACTICE");
        http.perform(get(path).header("Authorization", ownerToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventCount").value(1))
                .andExpect(jsonPath("$.data[0].events").doesNotExist());
        http.perform(get(path + "/" + take).header("Authorization", ownerToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[0].type").value("damp"));
        http.perform(get(path + "/" + take).header("Authorization", bearer(other))).andExpect(status().isBadRequest());
        jdbc.update("UPDATE gak_user_app_permission SET granted = FALSE WHERE user_id = ?", owner);
        http.perform(get(path).header("Authorization", ownerToken)).andExpect(status().isForbidden());
    }

    @Test
    void loginIssuesRestrictedSessionUntilPasswordChangeAndRevokesItOnCommit() throws Exception {
        long user = user(true);
        grant(user, "APP_TODO_LIST");
        var captcha = http.perform(get("/auth/captcha")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captcha").doesNotExist())
                .andExpect(jsonPath("$.data.image").isString()).andReturn();
        var session = (MockHttpSession) captcha.getRequest().getSession(false);
        var challenge = (LoginCaptcha) session.getAttribute(LoginCaptchaService.class.getName());
        var login = http.perform(post("/auth/login").session(session).contentType("application/json").content("""
                {"username":"p1_%d","encryptedPassword":"%s","captcha":"%s"}
                """.formatted(user, encrypt("InitialPass123!"), challenge.answer())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user.forceChangePassword").value(true)).andReturn();
        String token = "Bearer " + com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.data.token");
        String body = """
                {"oldEncryptedPassword":"%s","newEncryptedPassword":"%s"}
                """.formatted(encrypt("InitialPass123!"), encrypt("NewPass123!"));
        http.perform(post("/auth/change-password").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        http.perform(get("/todo-items").header("Authorization", token)).andExpect(status().isForbidden());
        http.perform(post("/auth/change-password").header("Authorization", token)
                .contentType("application/json").content(body)).andExpect(status().isOk());
        http.perform(get("/todo-items").header("Authorization", token)).andExpect(status().isUnauthorized());
        http.perform(get("/system/permissions/current-user/apps").header("Authorization", bearer(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.grantedFeatureCodes[0]").value("APP_TODO_LIST"));
    }

    @Test
    void publicKnowledgeQueryLoadsPublishedSampleWithoutAttachments() throws Exception {
        http.perform(get("/knowledge-base/public-highlights").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data[0].attachments").isEmpty());
    }

    @Test
    void batchWeeklyResetAndSeasonArchivePersistAllCharacters() {
        long owner = user(false);
        for (int index = 0; index < 2; index++) {
            jdbc.update("""
                    INSERT INTO gak_wow_character(id,owner_user_id,character_name,class_name,race_name,realm_name,
                        faction,level,item_level,mythic_best_level,mythic_dungeon_name,mythic_score,created_at,updated_at)
                    VALUES (?,?,'测试角色','牧师','人类','测试服','ALLIANCE',90,600,10,'测试副本',1000,now(),now())
                    """, IdWorker.getId(), owner);
        }
        assertEquals(2L, wow.resetAllWeeklyProgress(owner));
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM gak_wow_character_weekly_vault WHERE owner_user_id = ?", Integer.class, owner));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM gak_wow_character WHERE owner_user_id = ? AND mythic_dungeon_name IS NOT NULL",
                Integer.class, owner));
        assertEquals(2L, wow.archiveAndResetMythicSeason(owner));
        assertEquals(0L, wow.archiveAndResetMythicSeason(owner));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM gak_wow_character_mythic_season_history WHERE owner_user_id = ?",
                Integer.class, owner));
    }

    @Test
    void clearingCarrierClearsLinkedBillMerchant() {
        long owner = user(false);
        TravelRecord record = new TravelRecord();
        record.setId(IdWorker.getId());
        record.setOwnerUserId(owner);
        record.setSyncBill(true);
        record.setPaidAmount(new BigDecimal("100.00"));
        record.setCurrency("CNY");
        record.setDepartureDate(LocalDate.now());
        record.setDeparture("出发站");
        record.setArrival("到达站");
        record.setTransportType("TRAIN");
        record.setCarrier("测试承运商");
        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> travelBills.sync(record, null));
        String query = "SELECT merchant_name FROM gak_personal_bill WHERE owner_user_id = ? AND source_id = ?";
        assertEquals("测试承运商", jdbc.queryForObject(query, String.class, owner, record.getId()));
        record.setCarrier(null);
        transaction.executeWithoutResult(status -> travelBills.sync(record, null));
        assertNull(jdbc.queryForObject(query, String.class, owner, record.getId()));
    }

    private long user(boolean changeRequired) {
        long id = IdWorker.getId();
        jdbc.update("""
                INSERT INTO gak_user(id,username,password_hash,display_name,role_code,status,enabled,
                    force_change_password,created_at,updated_at)
                VALUES (?,?,?,'P1 回归账号','USER','ENABLED',TRUE,?,now(),now())
                """, id, "p1_" + id, passwords.encode("InitialPass123!"), changeRequired);
        return id;
    }

    private void grant(long user, String appCode) {
        jdbc.update("""
                INSERT INTO gak_user_app_permission(id,user_id,app_id,app_code,granted,created_at,updated_at)
                SELECT ?,?,id,app_code,TRUE,now(),now() FROM gak_system_app WHERE app_code=?
                """, IdWorker.getId(), user, appCode);
    }

    private String bearer(long user) {
        return "Bearer " + tokens.issueToken(user);
    }

    private String encrypt(String plain) throws Exception {
        byte[] key = Base64.getMimeDecoder().decode(crypto.getPublicKey()
                .replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", ""));
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key)));
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    }
}
