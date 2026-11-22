package com.gak.healthrecord.mapper;

import com.gak.healthrecord.domain.DentalCleaning;
import com.gak.healthrecord.domain.DentalReminder;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

/** 洁牙历史、提醒配置及调度状态。 */
@Mapper
public interface DentalCleaningMapper {
    String HEALTH_APP_ACCESS = """
            u.enabled AND u.status = 'ENABLED'
            AND EXISTS (SELECT 1 FROM gak_system_app a
                WHERE a.app_code = 'APP_HEALTH_RECORD' AND a.enabled
                  AND (u.role_code = 'ADMIN' OR EXISTS (
                      SELECT 1 FROM gak_user_app_permission p
                      WHERE p.user_id = u.id AND p.app_code = a.app_code AND p.granted)))
            """;

    @Select("SELECT EXISTS (SELECT 1 FROM gak_user u WHERE u.id = #{owner} AND " + HEALTH_APP_ACCESS + ")")
    boolean hasAccess(Long owner);

    @Select("""
            SELECT id, owner_user_id, cleaning_date, hospital_name, amount, note
            FROM gak_dental_cleaning WHERE owner_user_id = #{owner}
            ORDER BY cleaning_date DESC, id DESC
            """)
    List<DentalCleaning> list(Long owner);

    @Select("SELECT max(cleaning_date) FROM gak_dental_cleaning WHERE owner_user_id = #{owner}")
    LocalDate latestDate(Long owner);

    @Insert("""
            INSERT INTO gak_dental_cleaning (id, owner_user_id, cleaning_date, hospital_name, amount, note)
            VALUES (#{id}, #{ownerUserId}, #{cleaningDate}, #{hospitalName}, #{amount}, #{note})
            """)
    void insert(DentalCleaning cleaning);

    @Update("""
            UPDATE gak_dental_cleaning
            SET cleaning_date = #{cleaningDate}, hospital_name = #{hospitalName}, amount = #{amount}, note = #{note}
            WHERE owner_user_id = #{ownerUserId} AND id = #{id}
            """)
    int update(DentalCleaning cleaning);

    @Delete("DELETE FROM gak_dental_cleaning WHERE owner_user_id = #{owner} AND id = #{id}")
    int delete(@Param("owner") Long owner, @Param("id") Long id);

    @Insert("""
            INSERT INTO gak_dental_reminder (owner_user_id, enabled, interval_months, lead_days)
            VALUES (#{ownerUserId}, #{enabled}, #{intervalMonths}, #{leadDays})
            ON CONFLICT (owner_user_id) DO NOTHING
            """)
    void ensureReminder(DentalReminder reminder);

    @Select("""
            SELECT owner_user_id, enabled, interval_months, lead_days, next_due_date
            FROM gak_dental_reminder WHERE owner_user_id = #{owner}
            """)
    DentalReminder reminder(Long owner);

    @Select("""
            SELECT owner_user_id, enabled, interval_months, lead_days, next_due_date
            FROM gak_dental_reminder WHERE owner_user_id = #{owner} FOR UPDATE
            """)
    DentalReminder lockReminder(Long owner);

    @Update("""
            UPDATE gak_dental_reminder
            SET enabled = #{enabled}, interval_months = #{intervalMonths}, lead_days = #{leadDays},
                next_due_date = #{nextDueDate}
            WHERE owner_user_id = #{ownerUserId}
            """)
    void saveReminder(DentalReminder reminder);

    @Select("""
            SELECT r.owner_user_id FROM gak_dental_reminder r
            JOIN gak_user u ON u.id = r.owner_user_id
            WHERE r.enabled AND r.next_due_date - r.lead_days <= #{today}
              AND
            """ + HEALTH_APP_ACCESS + " ORDER BY r.next_due_date, r.owner_user_id")
    List<Long> dueOwners(LocalDate today);
}
