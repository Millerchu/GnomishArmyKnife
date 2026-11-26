package com.gak.datamigration.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.dto.PersonalBillsMigrationPayload;
import com.gak.datamigration.service.DataMigrationArchiveService;
import com.gak.datamigration.service.DataMigrationBeanMergeSupport;
import com.gak.datamigration.service.DataMigrationQuerySupport;
import com.gak.framework.exception.BusinessException;
import com.gak.personalbills.domain.PersonalBankCard;
import com.gak.personalbills.domain.PersonalBill;
import com.gak.personalbills.domain.PersonalBudget;
import com.gak.personalbills.domain.PersonalCreditAccount;
import com.gak.personalbills.mapper.PersonalBankCardMapper;
import com.gak.personalbills.mapper.PersonalBillMapper;
import com.gak.personalbills.mapper.PersonalBudgetMapper;
import com.gak.personalbills.mapper.PersonalCreditAccountMapper;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 个人账单迁移处理器。
 */
@Service
public class PersonalBillsMigrationHandler implements MigrationResourceHandler {

    private final PersonalBillMapper personalBillMapper;
    private final PersonalBudgetMapper personalBudgetMapper;
    private final PersonalCreditAccountMapper creditAccounts;
    private final PersonalBankCardMapper bankCards;
    private final UserMapper userMapper;
    private final DataMigrationArchiveService archiveService;

    public PersonalBillsMigrationHandler(PersonalBillMapper personalBillMapper,
                                         PersonalBudgetMapper personalBudgetMapper,
                                         UserMapper userMapper,
                                         DataMigrationArchiveService archiveService,
                                         PersonalCreditAccountMapper creditAccounts,
                                         PersonalBankCardMapper bankCards) {
        this.personalBillMapper = personalBillMapper;
        this.personalBudgetMapper = personalBudgetMapper;
        this.userMapper = userMapper;
        this.archiveService = archiveService;
        this.creditAccounts = creditAccounts;
        this.bankCards = bankCards;
    }

    @Override
    public String resourceCode() {
        return DataMigrationConstants.APP_PERSONAL_BILLS;
    }

    @Override
    public String resourceName() {
        return "个人账单";
    }

    @Override
    public String resourceType() {
        return DataMigrationConstants.RESOURCE_TYPE_BUSINESS;
    }

    @Override
    public boolean attachmentSupported() {
        return false;
    }

    @Override
    public String entryPath() {
        return "business/" + resourceCode() + "/data.json";
    }

    @Override
    public int order() {
        return 140;
    }

    @Override
    public MigrationResourceExportData exportData(ExportContext context) {
        QueryWrapper<PersonalBill> billWrapper = new QueryWrapper<>();
        // 行程及就诊关联账单随各自来源整体迁移，避免悬空关联或重复记账。
        billWrapper.and(query -> query.isNull("source_type").or().notIn("source_type", List.of("TRAVEL_RECORD", "HEALTH_VISIT")));
        billWrapper.notInSql("id", "SELECT detached_payment_bill_id FROM gak_travel_record "
                + "WHERE detached_payment_bill_id IS NOT NULL UNION SELECT detached_refund_bill_id "
                + "FROM gak_travel_record WHERE detached_refund_bill_id IS NOT NULL");
        billWrapper.orderByAsc("owner_user_id").orderByAsc("bill_date").orderByAsc("id");
        List<PersonalBill> bills = personalBillMapper.selectList(billWrapper);

        QueryWrapper<PersonalBudget> budgetWrapper = new QueryWrapper<>();
        budgetWrapper.orderByAsc("owner_user_id").orderByAsc("budget_year").orderByAsc("category_name").orderByAsc("id");
        List<PersonalBudget> budgets = personalBudgetMapper.selectList(budgetWrapper);

        List<PersonalCreditAccount> groups = creditAccounts.selectList(new QueryWrapper<PersonalCreditAccount>()
                .orderByAsc("owner_user_id", "id"));
        List<PersonalBankCard> cards = bankCards.selectList(new QueryWrapper<PersonalBankCard>()
                .orderByAsc("owner_user_id", "id"));
        long recordCount = (long) bills.size() + budgets.size() + groups.size() + cards.size();
        return new MigrationResourceExportData(resourceCode(), entryPath(),
                new PersonalBillsMigrationPayload(bills, budgets, groups, cards), recordCount, 0L, List.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MigrationResourceImportResult importData(ImportContext context) throws Exception {
        PersonalBillsMigrationPayload payload = archiveService.readJson(context.packageRoot(), entryPath(),
                PersonalBillsMigrationPayload.class);
        long importedCount = importFundingAccounts(context, payload);
        importedCount += importBills(context, DataMigrationQuerySupport.emptyIfNull(payload.bills()), payload.bankCards() != null);
        importedCount += importBudgets(context, DataMigrationQuerySupport.emptyIfNull(payload.budgets()));
        return MigrationResourceImportResult.success(importedCount, 0L, "个人账单导入完成");
    }

    private long importFundingAccounts(ImportContext context, PersonalBillsMigrationPayload payload) {
        List<PersonalCreditAccount> groups = DataMigrationQuerySupport.emptyIfNull(payload.creditAccounts());
        List<PersonalBankCard> cards = DataMigrationQuerySupport.emptyIfNull(payload.bankCards());
        for (PersonalCreditAccount group : groups) {
            group.setOwnerUserId(resolveUserId(group.getOwnerUserId(), context, "授信组"));
            restoreFundingRecord(context, creditAccounts, group, group.getId(), group.getOwnerUserId(),
                    PersonalCreditAccount::getOwnerUserId);
        }
        for (PersonalBankCard card : cards) {
            card.setOwnerUserId(resolveUserId(card.getOwnerUserId(), context, "银行卡"));
            if (card.getCreditAccountId() != null) {
                PersonalCreditAccount group = creditAccounts.selectById(card.getCreditAccountId());
                requireFundingOwner(group == null ? null : group.getOwnerUserId(), card.getOwnerUserId(), "授信组");
            }
            restoreFundingRecord(context, bankCards, card, card.getId(), card.getOwnerUserId(), PersonalBankCard::getOwnerUserId);
        }
        return (long) groups.size() + cards.size();
    }

    private <T> void restoreFundingRecord(ImportContext context, BaseMapper<T> mapper, T source, Long id,
                                          Long owner, Function<T, Long> ownerGetter) {
        if (id == null) {
            throw new BusinessException("DATA_MIGRATION_FUNDING_ID_REQUIRED", "资金账户记录缺少 ID");
        }
        T existing = mapper.selectById(id);
        if (existing == null) {
            mapper.insert(source);
            return;
        }
        requireFundingOwner(ownerGetter.apply(existing), owner, "资金账户");
        if (context.isStrict()) {
            throw new BusinessException("DATA_MIGRATION_FUNDING_CONFLICT", "资金账户已存在: " + id);
        }
        // 卡种、授信组及归档状态为一份快照，空授信组也需要参与恢复。
        if (DataMigrationBeanMergeSupport.overwriteNewest(source, existing, "id", "ownerUserId")) {
            mapper.updateById(existing);
        }
    }

    private void requireFundingOwner(Long actualOwner, Long expectedOwner, String label) {
        if (!Objects.equals(actualOwner, expectedOwner)) {
            throw new BusinessException("DATA_MIGRATION_FUNDING_OWNER_INVALID", label + "缺失或归属不一致");
        }
    }

    private long importBills(ImportContext context, List<PersonalBill> bills, boolean hasCardData) {
        long importedCount = 0L;
        for (PersonalBill source : bills) {
            if (source == null) {
                continue;
            }
            if ("HEALTH_VISIT".equals(source.getSourceType())) {
                throw new BusinessException("DATA_MIGRATION_HEALTH_RESOURCE_REQUIRED", "就诊关联账单需要通过健康资源恢复");
            }
            if ("TRAVEL_RECORD".equals(source.getSourceType())) {
                throw new BusinessException("DATA_MIGRATION_TRAVEL_RESOURCE_REQUIRED",
                        "行程关联账单需要通过个人行程记录资源恢复");
            }
            Long targetUserId = resolveUserId(source.getOwnerUserId(), context, "个人账单");
            if (source.getBankCardId() != null) {
                PersonalBankCard card = bankCards.selectById(source.getBankCardId());
                requireFundingOwner(card == null ? null : card.getOwnerUserId(), targetUserId, "账单银行卡");
            }
            PersonalBill existing = findExistingBill(source, targetUserId, hasCardData);
            if (existing != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_PERSONAL_BILL_CONFLICT", "个人账单已存在: " + source.getId());
                }
                source.setOwnerUserId(targetUserId);
                boolean applied = context.isOverwrite()
                        ? DataMigrationBeanMergeSupport.overwriteNewest(source, existing, "id", "ownerUserId", "accountName", "bankCardId")
                        : DataMigrationBeanMergeSupport.mergeNewestNonNull(source, existing, "id", "ownerUserId", "accountName", "bankCardId");
                if (applied) {
                    if (hasCardData || source.getBankCardId() != null) {
                        existing.setAccountName(source.getAccountName());
                        existing.setBankCardId(source.getBankCardId());
                    } else if (existing.getBankCardId() == null && source.getAccountName() != null) {
                        existing.setAccountName(source.getAccountName());
                    }
                    personalBillMapper.updateById(existing);
                }
            } else {
                PersonalBill insertBill = copyBill(source);
                insertBill.setOwnerUserId(targetUserId);
                personalBillMapper.insert(insertBill);
            }
            importedCount++;
        }
        return importedCount;
    }

    private long importBudgets(ImportContext context, List<PersonalBudget> budgets) {
        long importedCount = 0L;
        for (PersonalBudget source : budgets) {
            if (source == null) {
                continue;
            }
            Long targetUserId = resolveUserId(source.getOwnerUserId(), context, "个人预算");
            PersonalBudget existing = findExistingBudget(source, targetUserId);
            if (existing != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_PERSONAL_BUDGET_CONFLICT", "个人预算已存在: " + source.getId());
                }
                source.setOwnerUserId(targetUserId);
                if (context.isOverwrite()) {
                    DataMigrationBeanMergeSupport.overwriteNewest(source, existing, "id", "ownerUserId");
                } else {
                    DataMigrationBeanMergeSupport.mergeNewestNonNull(source, existing, "id", "ownerUserId");
                }
                existing.setOwnerUserId(targetUserId);
                personalBudgetMapper.updateById(existing);
            } else {
                PersonalBudget insertBudget = copyBudget(source);
                insertBudget.setOwnerUserId(targetUserId);
                personalBudgetMapper.insert(insertBudget);
            }
            importedCount++;
        }
        return importedCount;
    }

    private PersonalBill findExistingBill(PersonalBill source, Long targetUserId, boolean hasCardData) {
        PersonalBill byId = source.getId() == null ? null : personalBillMapper.selectById(source.getId());
        if (byId != null) {
            if (!java.util.Objects.equals(byId.getOwnerUserId(), targetUserId) || byId.getSourceType() != null) {
                throw new BusinessException("DATA_MIGRATION_PERSONAL_BILL_SOURCE_CONFLICT",
                        "账单 ID 与其他用户或行程来源账单冲突，请通过行程资源恢复");
            }
            return byId;
        }
        QueryWrapper<PersonalBill> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", targetUserId)
                .isNull("source_type")
                .eq("currency", source.getCurrency() == null ? "CNY" : source.getCurrency())
                .eq("bill_date", source.getBillDate())
                .eq("bill_type", source.getBillType())
                .eq("amount", source.getAmount());
        wrapper.notInSql("id", "SELECT detached_payment_bill_id FROM gak_travel_record "
                + "WHERE detached_payment_bill_id IS NOT NULL UNION SELECT detached_refund_bill_id "
                + "FROM gak_travel_record WHERE detached_refund_bill_id IS NOT NULL");
        DataMigrationQuerySupport.eqNullable(wrapper, "category_name", source.getCategoryName());
        DataMigrationQuerySupport.eqNullable(wrapper, "account_name", source.getAccountName());
        if (hasCardData) {
            DataMigrationQuerySupport.eqNullable(wrapper, "bank_card_id", source.getBankCardId());
        }
        DataMigrationQuerySupport.eqNullable(wrapper, "merchant_name", source.getMerchantName());
        return personalBillMapper.selectOne(wrapper);
    }

    private PersonalBudget findExistingBudget(PersonalBudget source, Long targetUserId) {
        PersonalBudget byId = source.getId() == null ? null : personalBudgetMapper.selectById(source.getId());
        if (byId != null) {
            return byId;
        }
        // 预算允许同类多项目，仅用稳定 ID 匹配，避免导入时吞掉同分类明细。
        return null;
    }

    private Long resolveUserId(Long sourceUserId, ImportContext context, String resourceName) {
        Long mappedId = context.mappedUserId(sourceUserId);
        if (mappedId != null) {
            return mappedId;
        }
        User sameUser = sourceUserId == null ? null : userMapper.selectById(sourceUserId);
        if (sameUser == null) {
            throw new BusinessException("DATA_MIGRATION_USER_MISSING", resourceName + "依赖的用户不存在: " + sourceUserId);
        }
        return sameUser.getId();
    }

    private PersonalBill copyBill(PersonalBill source) {
        PersonalBill bill = new PersonalBill();
        DataMigrationBeanMergeSupport.overwrite(source, bill);
        return bill;
    }

    private PersonalBudget copyBudget(PersonalBudget source) {
        PersonalBudget budget = new PersonalBudget();
        DataMigrationBeanMergeSupport.overwrite(source, budget);
        return budget;
    }
}
