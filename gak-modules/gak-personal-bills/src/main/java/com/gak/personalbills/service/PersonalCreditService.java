package com.gak.personalbills.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.personalbills.domain.PersonalCreditAccount;
import com.gak.personalbills.dto.SaveCreditAccountRequest;
import com.gak.personalbills.mapper.PersonalCreditAccountMapper;
import com.gak.personalbills.vo.PersonalCreditAccountVO;
import com.gak.personalbills.vo.PersonalCreditSummaryVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 维护手工授信快照，消费账单不自动抵扣，避免遗漏还款造成额度失真。 */
@Service
public class PersonalCreditService {
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private final PersonalCreditAccountMapper mapper;

    public PersonalCreditService(PersonalCreditAccountMapper mapper) { this.mapper = mapper; }

    /** 按当前用户汇总独立授信；超额使用时可用额度最低为零。 */
    public PersonalCreditSummaryVO summary(Long userId) {
        List<PersonalCreditAccountVO> accounts = mapper.selectList(new QueryWrapper<PersonalCreditAccount>()
                .eq("owner_user_id", userId).orderByDesc("updated_at").orderByDesc("id"))
                .stream().map(this::toVO).toList();
        PersonalCreditSummaryVO result = new PersonalCreditSummaryVO();
        result.setAccounts(accounts);
        result.setTotalLimit(accounts.stream().map(PersonalCreditAccountVO::getCreditLimit).reduce(ZERO_MONEY, BigDecimal::add));
        result.setTotalUsed(accounts.stream().map(PersonalCreditAccountVO::getUsedAmount).reduce(ZERO_MONEY, BigDecimal::add));
        result.setTotalAvailable(accounts.stream().map(PersonalCreditAccountVO::getAvailableAmount).reduce(ZERO_MONEY, BigDecimal::add));
        result.setUsageRate(result.getTotalLimit().signum() == 0 ? BigDecimal.ZERO.setScale(4)
                : result.getTotalUsed().divide(result.getTotalLimit(), 4, RoundingMode.HALF_UP));
        return result;
    }

    /** 保存授信账户，更新前校验归属。 */
    @Transactional
    public PersonalCreditAccountVO save(Long userId, Long id, SaveCreditAccountRequest request) {
        PersonalCreditAccount account = id == null ? new PersonalCreditAccount() : owned(userId, id);
        account.setOwnerUserId(userId);
        account.setAccountName(request.getAccountName().trim());
        account.setInstitution(request.getInstitution().trim());
        account.setCreditLimit(request.getCreditLimit().setScale(2, RoundingMode.HALF_UP));
        account.setUsedAmount(request.getUsedAmount().setScale(2, RoundingMode.HALF_UP));
        account.setBillingDay(request.getBillingDay());
        account.setRepaymentDay(request.getRepaymentDay());
        account.setNote(request.getNote() == null ? "" : request.getNote().trim());
        account.setUpdatedAt(LocalDateTime.now());
        if (id == null) {
            account.setCreatedAt(account.getUpdatedAt());
            mapper.insert(account);
        } else {
            // 显式更新可空日期，允许用户清除原有账单日和还款日。
            mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<PersonalCreditAccount>()
                    .eq("id", id).eq("owner_user_id", userId)
                    .set("account_name", account.getAccountName()).set("institution", account.getInstitution())
                    .set("credit_limit", account.getCreditLimit()).set("used_amount", account.getUsedAmount())
                    .set("note", account.getNote()).set("updated_at", account.getUpdatedAt())
                    .set("billing_day", request.getBillingDay()).set("repayment_day", request.getRepaymentDay()));
        }
        return toVO(account);
    }

    /** 删除当前用户的授信账户。 */
    @Transactional
    public void delete(Long userId, Long id) { mapper.deleteById(owned(userId, id).getId()); }

    private PersonalCreditAccount owned(Long userId, Long id) {
        PersonalCreditAccount account = mapper.selectById(id);
        if (account == null || !Objects.equals(account.getOwnerUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "授信账户不存在");
        }
        return account;
    }

    private PersonalCreditAccountVO toVO(PersonalCreditAccount account) {
        PersonalCreditAccountVO result = new PersonalCreditAccountVO();
        result.setId(account.getId());
        result.setAccountName(account.getAccountName());
        result.setInstitution(account.getInstitution());
        result.setCreditLimit(account.getCreditLimit());
        result.setUsedAmount(account.getUsedAmount());
        result.setAvailableAmount(account.getCreditLimit().subtract(account.getUsedAmount()).max(ZERO_MONEY).setScale(2, RoundingMode.HALF_UP));
        result.setBillingDay(account.getBillingDay());
        result.setRepaymentDay(account.getRepaymentDay());
        result.setNote(account.getNote());
        result.setUpdatedAt(account.getUpdatedAt());
        return result;
    }
}
