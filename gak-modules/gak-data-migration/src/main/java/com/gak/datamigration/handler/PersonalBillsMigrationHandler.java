package com.gak.datamigration.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.service.DataMigrationArchiveService;
import com.gak.datamigration.service.DataMigrationBeanMergeSupport;
import com.gak.datamigration.service.DataMigrationQuerySupport;
import com.gak.framework.exception.BusinessException;
import com.gak.personalbills.domain.PersonalBill;
import com.gak.personalbills.domain.PersonalBudget;
import com.gak.personalbills.mapper.PersonalBillMapper;
import com.gak.personalbills.mapper.PersonalBudgetMapper;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 个人账单迁移处理器。
 */
@Service
public class PersonalBillsMigrationHandler implements MigrationResourceHandler {

    private final PersonalBillMapper personalBillMapper;
    private final PersonalBudgetMapper personalBudgetMapper;
    private final UserMapper userMapper;
    private final DataMigrationArchiveService archiveService;

    public PersonalBillsMigrationHandler(PersonalBillMapper personalBillMapper,
                                         PersonalBudgetMapper personalBudgetMapper,
                                         UserMapper userMapper,
                                         DataMigrationArchiveService archiveService) {
        this.personalBillMapper = personalBillMapper;
        this.personalBudgetMapper = personalBudgetMapper;
        this.userMapper = userMapper;
        this.archiveService = archiveService;
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
        billWrapper.orderByAsc("owner_user_id").orderByAsc("bill_date").orderByAsc("id");
        List<PersonalBill> bills = personalBillMapper.selectList(billWrapper);

        QueryWrapper<PersonalBudget> budgetWrapper = new QueryWrapper<>();
        budgetWrapper.orderByAsc("owner_user_id").orderByAsc("budget_year").orderByAsc("category_name").orderByAsc("id");
        List<PersonalBudget> budgets = personalBudgetMapper.selectList(budgetWrapper);

        long recordCount = (long) bills.size() + budgets.size();
        return new MigrationResourceExportData(resourceCode(), entryPath(), new Payload(bills, budgets), recordCount, 0L, List.of());
    }

    @Override
    @Transactional
    public MigrationResourceImportResult importData(ImportContext context) throws Exception {
        Payload payload = archiveService.readJson(context.packageRoot(), entryPath(), Payload.class);
        long importedCount = importBills(context, DataMigrationQuerySupport.emptyIfNull(payload.getBills()));
        importedCount += importBudgets(context, DataMigrationQuerySupport.emptyIfNull(payload.getBudgets()));
        return MigrationResourceImportResult.success(importedCount, 0L, "个人账单导入完成");
    }

    private long importBills(ImportContext context, List<PersonalBill> bills) {
        long importedCount = 0L;
        for (PersonalBill source : bills) {
            if (source == null) {
                continue;
            }
            Long targetUserId = resolveUserId(source.getOwnerUserId(), context, "个人账单");
            PersonalBill existing = findExistingBill(source, targetUserId);
            if (existing != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_PERSONAL_BILL_CONFLICT", "个人账单已存在: " + source.getId());
                }
                source.setOwnerUserId(targetUserId);
                if (context.isOverwrite()) {
                    DataMigrationBeanMergeSupport.overwriteNewest(source, existing, "id", "ownerUserId");
                } else {
                    DataMigrationBeanMergeSupport.mergeNewestNonNull(source, existing, "id", "ownerUserId");
                }
                existing.setOwnerUserId(targetUserId);
                personalBillMapper.updateById(existing);
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

    private PersonalBill findExistingBill(PersonalBill source, Long targetUserId) {
        PersonalBill byId = source.getId() == null ? null : personalBillMapper.selectById(source.getId());
        if (byId != null) {
            return byId;
        }
        QueryWrapper<PersonalBill> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", targetUserId)
                .eq("bill_date", source.getBillDate())
                .eq("bill_type", source.getBillType())
                .eq("amount", source.getAmount());
        DataMigrationQuerySupport.eqNullable(wrapper, "category_name", source.getCategoryName());
        DataMigrationQuerySupport.eqNullable(wrapper, "account_name", source.getAccountName());
        DataMigrationQuerySupport.eqNullable(wrapper, "merchant_name", source.getMerchantName());
        return personalBillMapper.selectOne(wrapper);
    }

    private PersonalBudget findExistingBudget(PersonalBudget source, Long targetUserId) {
        PersonalBudget byId = source.getId() == null ? null : personalBudgetMapper.selectById(source.getId());
        if (byId != null) {
            return byId;
        }
        QueryWrapper<PersonalBudget> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", targetUserId)
                .eq("budget_year", source.getBudgetYear())
                .eq("category_name", source.getCategoryName());
        return personalBudgetMapper.selectOne(wrapper);
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

    /**
     * 个人账单导出载荷。
     */
    public static class Payload {

        private List<PersonalBill> bills;
        private List<PersonalBudget> budgets;

        public Payload() {
        }

        public Payload(List<PersonalBill> bills, List<PersonalBudget> budgets) {
            this.bills = bills;
            this.budgets = budgets;
        }

        public List<PersonalBill> getBills() {
            return bills;
        }

        public void setBills(List<PersonalBill> bills) {
            this.bills = bills;
        }

        public List<PersonalBudget> getBudgets() {
            return budgets;
        }

        public void setBudgets(List<PersonalBudget> budgets) {
            this.budgets = budgets;
        }
    }
}
