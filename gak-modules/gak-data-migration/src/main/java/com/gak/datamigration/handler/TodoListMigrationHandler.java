package com.gak.datamigration.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.datamigration.DataMigrationConstants;
import com.gak.datamigration.service.DataMigrationArchiveService;
import com.gak.datamigration.service.DataMigrationBeanMergeSupport;
import com.gak.framework.exception.BusinessException;
import com.gak.todolist.domain.TodoItem;
import com.gak.todolist.domain.TodoItemStep;
import com.gak.todolist.mapper.TodoItemMapper;
import com.gak.todolist.mapper.TodoItemStepMapper;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 待办列表迁移处理器。
 */
@Service
public class TodoListMigrationHandler implements MigrationResourceHandler {

    private static final String APP_CODE = "APP_TODO_LIST";

    private final TodoItemMapper todoItemMapper;
    private final TodoItemStepMapper todoItemStepMapper;
    private final UserMapper userMapper;
    private final DataMigrationArchiveService archiveService;

    public TodoListMigrationHandler(TodoItemMapper todoItemMapper,
                                    TodoItemStepMapper todoItemStepMapper,
                                    UserMapper userMapper,
                                    DataMigrationArchiveService archiveService) {
        this.todoItemMapper = todoItemMapper;
        this.todoItemStepMapper = todoItemStepMapper;
        this.userMapper = userMapper;
        this.archiveService = archiveService;
    }

    @Override
    public String resourceCode() {
        return APP_CODE;
    }

    @Override
    public String resourceName() {
        return "待办列表";
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
        return "business/" + APP_CODE + "/data.json";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public MigrationResourceExportData exportData(ExportContext context) {
        QueryWrapper<TodoItem> itemWrapper = new QueryWrapper<>();
        itemWrapper.orderByAsc("created_at").orderByAsc("id");
        List<TodoItem> items = todoItemMapper.selectList(itemWrapper);

        QueryWrapper<TodoItemStep> stepWrapper = new QueryWrapper<>();
        stepWrapper.orderByAsc("task_id").orderByAsc("sort_no").orderByAsc("id");
        List<TodoItemStep> steps = todoItemStepMapper.selectList(stepWrapper);

        long recordCount = (long) items.size() + steps.size();
        return new MigrationResourceExportData(resourceCode(), entryPath(), new Payload(items, steps), recordCount, 0L, List.of());
    }

    @Override
    @Transactional
    public MigrationResourceImportResult importData(ImportContext context) throws Exception {
        Payload payload = archiveService.readJson(context.packageRoot(), entryPath(), Payload.class);
        Map<Long, List<TodoItemStep>> stepMap = buildStepMap(payload.getSteps());
        long importedCount = 0L;

        for (TodoItem source : payload.getItems()) {
            if (source == null) {
                continue;
            }
            Long targetUserId = resolveUserId(source.getOwnerUserId(), context);
            if (targetUserId == null) {
                throw new BusinessException("DATA_MIGRATION_TODO_USER_MISSING", "待办依赖的用户不存在: " + source.getOwnerUserId());
            }

            TodoItem existing = source.getId() == null ? null : todoItemMapper.selectById(source.getId());
            Long targetTaskId;
            if (existing != null) {
                if (context.isStrict()) {
                    throw new BusinessException("DATA_MIGRATION_TODO_CONFLICT", "待办任务已存在: " + source.getId());
                }
                existing.setOwnerUserId(targetUserId);
                if (context.isOverwrite()) {
                    DataMigrationBeanMergeSupport.overwrite(source, existing, "id", "ownerUserId");
                } else {
                    DataMigrationBeanMergeSupport.mergeNewestNonNull(source, existing, "id", "ownerUserId");
                }
                existing.setOwnerUserId(targetUserId);
                todoItemMapper.updateById(existing);
                targetTaskId = existing.getId();
            } else {
                TodoItem insertItem = copyItem(source);
                insertItem.setOwnerUserId(targetUserId);
                if (insertItem.getId() != null && todoItemMapper.selectById(insertItem.getId()) != null) {
                    if (context.isStrict()) {
                        throw new BusinessException("DATA_MIGRATION_TODO_ID_CONFLICT", "待办任务 ID 冲突: " + insertItem.getId());
                    }
                    insertItem.setId(null);
                }
                todoItemMapper.insert(insertItem);
                targetTaskId = insertItem.getId();
            }
            importedCount++;

            List<TodoItemStep> steps = stepMap.getOrDefault(source.getId(), List.of());
            for (TodoItemStep sourceStep : steps) {
                TodoItemStep existingStep = findExistingStep(targetTaskId, sourceStep);
                if (existingStep != null) {
                    if (context.isStrict()) {
                        throw new BusinessException("DATA_MIGRATION_TODO_STEP_CONFLICT", "待办步骤已存在: " + sourceStep.getTitle());
                    }
                    sourceStep.setTaskId(targetTaskId);
                    if (context.isOverwrite()) {
                        DataMigrationBeanMergeSupport.overwriteNewest(sourceStep, existingStep, "id", "taskId");
                    } else {
                        DataMigrationBeanMergeSupport.mergeNewestNonNull(sourceStep, existingStep, "id", "taskId");
                    }
                    existingStep.setTaskId(targetTaskId);
                    todoItemStepMapper.updateById(existingStep);
                } else {
                    TodoItemStep insertStep = copyStep(sourceStep);
                    insertStep.setTaskId(targetTaskId);
                    todoItemStepMapper.insert(insertStep);
                }
                importedCount++;
            }
        }

        return MigrationResourceImportResult.success(importedCount, 0L, "待办列表导入完成");
    }

    private Map<Long, List<TodoItemStep>> buildStepMap(List<TodoItemStep> steps) {
        Map<Long, List<TodoItemStep>> result = new LinkedHashMap<>();
        for (TodoItemStep step : steps) {
            result.computeIfAbsent(step.getTaskId(), key -> new ArrayList<>()).add(step);
        }
        return result;
    }

    private TodoItemStep findExistingStep(Long taskId, TodoItemStep sourceStep) {
        TodoItemStep byId = sourceStep.getId() == null ? null : todoItemStepMapper.selectById(sourceStep.getId());
        if (byId != null) {
            return byId;
        }
        QueryWrapper<TodoItemStep> wrapper = new QueryWrapper<>();
        wrapper.eq("task_id", taskId)
                .eq("title", sourceStep.getTitle())
                .eq("sort_no", sourceStep.getSortNo());
        return todoItemStepMapper.selectOne(wrapper);
    }

    private Long resolveUserId(Long sourceUserId, ImportContext context) {
        Long mappedId = context.mappedUserId(sourceUserId);
        if (mappedId != null) {
            return mappedId;
        }
        User sameUser = sourceUserId == null ? null : userMapper.selectById(sourceUserId);
        return sameUser == null ? null : sameUser.getId();
    }

    private TodoItem copyItem(TodoItem source) {
        TodoItem item = new TodoItem();
        DataMigrationBeanMergeSupport.overwrite(source, item);
        return item;
    }

    private TodoItemStep copyStep(TodoItemStep source) {
        TodoItemStep step = new TodoItemStep();
        DataMigrationBeanMergeSupport.overwrite(source, step);
        return step;
    }

    /**
     * 待办导出载荷。
     */
    public static class Payload {

        private List<TodoItem> items;
        private List<TodoItemStep> steps;

        public Payload() {
        }

        public Payload(List<TodoItem> items, List<TodoItemStep> steps) {
            this.items = items;
            this.steps = steps;
        }

        public List<TodoItem> getItems() {
            return items;
        }

        public void setItems(List<TodoItem> items) {
            this.items = items;
        }

        public List<TodoItemStep> getSteps() {
            return steps;
        }

        public void setSteps(List<TodoItemStep> steps) {
            this.steps = steps;
        }
    }
}
