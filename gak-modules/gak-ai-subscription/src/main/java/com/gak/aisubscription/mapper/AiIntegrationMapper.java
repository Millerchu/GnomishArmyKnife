package com.gak.aisubscription.mapper;
import com.gak.aisubscription.domain.AiBudget;
import com.gak.aisubscription.domain.AiBillLink;
import java.util.List;
import org.apache.ibatis.annotations.*;
/** 调用方先锁用户行，预算与关联变更在同一事务内完成。 */
@Mapper
public interface AiIntegrationMapper {
    @Select("SELECT owner_user_id,budget_month,currency,amount,threshold FROM gak_ai_budget WHERE owner_user_id=#{owner} AND budget_month=#{month} ORDER BY currency")
    List<AiBudget> budgets(@Param("owner") Long owner,@Param("month") String month);
    @Insert("INSERT INTO gak_ai_budget(owner_user_id,budget_month,currency,amount,threshold) VALUES(#{ownerUserId},#{budgetMonth},#{currency},#{amount},#{threshold}) ON CONFLICT(owner_user_id,budget_month,currency) DO UPDATE SET amount=EXCLUDED.amount,threshold=EXCLUDED.threshold")
    void saveBudget(AiBudget budget);
    @Select("SELECT owner_user_id,payment_id,bill_id,generated FROM gak_ai_bill_link WHERE owner_user_id=#{owner} AND payment_id=#{payment}")
    AiBillLink link(@Param("owner") Long owner,@Param("payment") Long payment);
    @Select("SELECT payment_id FROM gak_ai_bill_link WHERE bill_id=#{bill}")
    Long linkedPayment(@Param("bill") Long bill);
    @Insert("INSERT INTO gak_ai_bill_link(owner_user_id,payment_id,bill_id,generated) VALUES(#{ownerUserId},#{paymentId},#{billId},#{generated})")
    void insertLink(AiBillLink link);
    @Delete("DELETE FROM gak_ai_bill_link WHERE owner_user_id=#{owner} AND payment_id=#{payment}")
    void unlink(@Param("owner") Long owner,@Param("payment") Long payment);
}
