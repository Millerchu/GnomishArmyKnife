package com.gak.todolist.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.framework.dictionary.DataDictionaryUsageSupport;
import com.gak.framework.dictionary.vo.DictionaryOptionVO;
import com.gak.framework.exception.BusinessException;
import com.gak.todolist.domain.TodoItem;
import com.gak.todolist.domain.TodoItemStep;
import com.gak.todolist.dto.SaveTodoItemRequest;
import com.gak.todolist.dto.TodoItemQueryRequest;
import com.gak.todolist.dto.TodoItemStepRequest;
import com.gak.todolist.dto.UpdateTodoImportantRequest;
import com.gak.todolist.dto.UpdateTodoStatusRequest;
import com.gak.todolist.enums.TodoViewCode;
import com.gak.todolist.mapper.TodoItemMapper;
import com.gak.todolist.mapper.TodoItemStepMapper;
import com.gak.todolist.vo.ListStatVO;
import com.gak.todolist.vo.TodoItemListVO;
import com.gak.todolist.vo.TodoItemPageVO;
import com.gak.todolist.vo.TodoItemSimpleVO;
import com.gak.todolist.vo.TodoItemStepVO;
import com.gak.todolist.vo.TodoSummaryVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 待办任务服务。
 */
@Service
public class TodoItemService {

    private static final String APP_CODE = "APP_TODO_LIST";
    private static final String MODULE_CODE = "TODO_ITEM";
    private static final String LIST_CODE_FIELD = "listCode";
    private static final String STATUS_FIELD = "status";
    private static final String IMPORTANCE_FIELD = "importance";
    private static final String MY_DAY_LIST_CODE = "MY_DAY";
    private static final String COMPLETED_STATUS = "COMPLETED";
    private static final int UPCOMING_LIMIT = 5;
    private static final Comparator<TodoItem> ITEM_ORDER = Comparator
            .comparing(TodoItemService::isCompleted)
            .thenComparing(item -> !Boolean.TRUE.equals(item.getImportant()))
            .thenComparing(TodoItem::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TodoItem::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TodoItem::getId, Comparator.nullsLast(Comparator.reverseOrder()));

    private final TodoItemMapper todoItemMapper;
    private final TodoItemStepMapper todoItemStepMapper;
    private final UserMapper userMapper;
    private final DataDictionaryUsageSupport dataDictionaryUsageSupport;

    public TodoItemService(TodoItemMapper todoItemMapper,
                           TodoItemStepMapper todoItemStepMapper,
                           UserMapper userMapper,
                           DataDictionaryUsageSupport dataDictionaryUsageSupport) {
        this.todoItemMapper = todoItemMapper;
        this.todoItemStepMapper = todoItemStepMapper;
        this.userMapper = userMapper;
        this.dataDictionaryUsageSupport = dataDictionaryUsageSupport;
    }

    public TodoItemPageVO page(Long currentUserId, TodoItemQueryRequest request) {
        ensureCurrentUserExists(currentUserId);

        String normalizedListCode = normalizeOptionalListCode(request.getListCode());
        String normalizedStatus = normalizeOptionalStatus(request.getStatus());
        String normalizedImportance = normalizeOptionalImportance(request.getImportance());
        String normalizedViewCode = normalizeOptionalViewCode(request.getViewCode());

        List<TodoItem> allItems = loadAllItems(currentUserId);
        Map<Long, List<TodoItemStep>> stepMap = loadStepMap(allItems);

        List<TodoItem> filteredItems = new ArrayList<>();
        for (TodoItem item : allItems) {
            if (matchesFilters(item, stepMap.getOrDefault(item.getId(), List.of()), request.getKeyword(),
                    normalizedListCode, normalizedStatus, normalizedImportance, normalizedViewCode)) {
                filteredItems.add(item);
            }
        }
        filteredItems.sort(ITEM_ORDER);

        long total = filteredItems.size();
        long fromIndex = Math.max((request.getPageNo() - 1) * request.getPageSize(), 0L);
        long toIndex = Math.min(fromIndex + request.getPageSize(), total);
        List<TodoItemListVO> list = new ArrayList<>();
        if (fromIndex < total) {
            for (TodoItem item : filteredItems.subList((int) fromIndex, (int) toIndex)) {
                list.add(toListVO(item, stepMap.getOrDefault(item.getId(), List.of())));
            }
        }

        TodoItemPageVO result = new TodoItemPageVO();
        result.setList(list);
        result.setTotal(total);
        result.setSummary(buildSummary(allItems));
        result.setUpcoming(buildUpcoming(allItems));
        result.setListStats(buildListStats(allItems));
        return result;
    }

    @Transactional
    public TodoItemListVO create(Long currentUserId, SaveTodoItemRequest request) {
        ensureCurrentUserExists(currentUserId);
        NormalizedTodo normalizedTodo = normalizeRequest(request);

        LocalDateTime now = LocalDateTime.now();
        TodoItem item = new TodoItem();
        item.setOwnerUserId(currentUserId);
        applyNormalized(item, normalizedTodo);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        todoItemMapper.insert(item);

        saveSteps(item.getId(), normalizedTodo.steps(), now);
        return toListVO(item, loadStepsByTaskId(item.getId()));
    }

    @Transactional
    public TodoItemListVO update(Long currentUserId, Long id, SaveTodoItemRequest request) {
        ensureCurrentUserExists(currentUserId);
        TodoItem current = getOwnedItemOrThrow(currentUserId, id);
        NormalizedTodo normalizedTodo = normalizeRequest(request);

        applyNormalized(current, normalizedTodo);
        current.setUpdatedAt(LocalDateTime.now());
        todoItemMapper.updateById(current);

        deleteStepsByTaskIds(List.of(id));
        saveSteps(id, normalizedTodo.steps(), LocalDateTime.now());
        return toListVO(current, loadStepsByTaskId(id));
    }

    @Transactional
    public void delete(Long currentUserId, Long id) {
        ensureCurrentUserExists(currentUserId);
        TodoItem item = getOwnedItemOrThrow(currentUserId, id);
        deleteStepsByTaskIds(List.of(item.getId()));
        todoItemMapper.deleteById(item.getId());
    }

    @Transactional
    public void updateStatus(Long currentUserId, Long id, UpdateTodoStatusRequest request) {
        ensureCurrentUserExists(currentUserId);
        TodoItem item = getOwnedItemOrThrow(currentUserId, id);
        String normalizedStatus = normalizeRequiredStatus(request.getStatus());
        validateCompletedFlag(request.getCompleted(), normalizedStatus);

        TodoItem updated = new TodoItem();
        updated.setId(item.getId());
        updated.setStatus(normalizedStatus);
        updated.setUpdatedAt(LocalDateTime.now());
        todoItemMapper.updateById(updated);
    }

    @Transactional
    public void updateImportant(Long currentUserId, Long id, UpdateTodoImportantRequest request) {
        ensureCurrentUserExists(currentUserId);
        TodoItem item = getOwnedItemOrThrow(currentUserId, id);

        TodoItem updated = new TodoItem();
        updated.setId(item.getId());
        updated.setImportant(request.getImportant());
        updated.setUpdatedAt(LocalDateTime.now());
        todoItemMapper.updateById(updated);
    }

    @Transactional
    public void clearCompleted(Long currentUserId) {
        ensureCurrentUserExists(currentUserId);

        QueryWrapper<TodoItem> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", currentUserId).eq("status", COMPLETED_STATUS);
        List<TodoItem> completedItems = todoItemMapper.selectList(wrapper);
        if (completedItems.isEmpty()) {
            return;
        }

        List<Long> ids = new ArrayList<>();
        for (TodoItem item : completedItems) {
            ids.add(item.getId());
        }
        deleteStepsByTaskIds(ids);

        QueryWrapper<TodoItem> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("owner_user_id", currentUserId).eq("status", COMPLETED_STATUS);
        todoItemMapper.delete(deleteWrapper);
    }

    private List<TodoItem> loadAllItems(Long currentUserId) {
        QueryWrapper<TodoItem> wrapper = new QueryWrapper<>();
        wrapper.eq("owner_user_id", currentUserId);
        return new ArrayList<>(todoItemMapper.selectList(wrapper));
    }

    private Map<Long, List<TodoItemStep>> loadStepMap(List<TodoItem> items) {
        Map<Long, List<TodoItemStep>> result = new HashMap<>();
        if (items.isEmpty()) {
            return result;
        }

        List<Long> ids = new ArrayList<>();
        for (TodoItem item : items) {
            ids.add(item.getId());
        }
        QueryWrapper<TodoItemStep> wrapper = new QueryWrapper<>();
        wrapper.in("task_id", ids);
        List<TodoItemStep> steps = new ArrayList<>(todoItemStepMapper.selectList(wrapper));
        steps.sort(Comparator.comparing(TodoItemStep::getSortNo, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TodoItemStep::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TodoItemStep::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        for (TodoItemStep step : steps) {
            result.computeIfAbsent(step.getTaskId(), key -> new ArrayList<>()).add(step);
        }
        return result;
    }

    private List<TodoItemStep> loadStepsByTaskId(Long taskId) {
        QueryWrapper<TodoItemStep> wrapper = new QueryWrapper<>();
        wrapper.eq("task_id", taskId);
        wrapper.orderByAsc("sort_no").orderByAsc("created_at").orderByAsc("id");
        return todoItemStepMapper.selectList(wrapper);
    }

    private boolean matchesFilters(TodoItem item,
                                   List<TodoItemStep> steps,
                                   String keyword,
                                   String listCode,
                                   String status,
                                   String importance,
                                   String viewCode) {
        if (listCode != null && !listCode.equals(item.getListCode())) {
            return false;
        }
        if (status != null && !status.equals(item.getStatus())) {
            return false;
        }
        if (importance != null && !importance.equals(item.getImportance())) {
            return false;
        }
        if (!matchesViewCode(item, viewCode)) {
            return false;
        }
        return matchesKeyword(item, steps, keyword);
    }

    private boolean matchesViewCode(TodoItem item, String viewCode) {
        if (viewCode == null || TodoViewCode.ALL.name().equals(viewCode)) {
            return true;
        }
        if (TodoViewCode.TODAY.name().equals(viewCode)) {
            return !isCompleted(item)
                    && (LocalDate.now().equals(item.getDueDate()) || MY_DAY_LIST_CODE.equals(item.getListCode()));
        }
        if (TodoViewCode.IMPORTANT.name().equals(viewCode)) {
            return !isCompleted(item) && Boolean.TRUE.equals(item.getImportant());
        }
        if (TodoViewCode.COMPLETED.name().equals(viewCode)) {
            return isCompleted(item);
        }
        return true;
    }

    private boolean matchesKeyword(TodoItem item, List<TodoItemStep> steps, String keyword) {
        String trimmedKeyword = trimToNull(keyword);
        if (trimmedKeyword == null) {
            return true;
        }
        String needle = trimmedKeyword.toLowerCase();
        if (containsIgnoreCase(item.getTitle(), needle) || containsIgnoreCase(item.getNote(), needle)) {
            return true;
        }
        for (TodoItemStep step : steps) {
            if (containsIgnoreCase(step.getTitle(), needle)) {
                return true;
            }
        }
        return false;
    }

    private TodoSummaryVO buildSummary(List<TodoItem> items) {
        TodoSummaryVO summary = new TodoSummaryVO();
        long todayCount = 0;
        long importantCount = 0;
        long completedCount = 0;
        LocalDate today = LocalDate.now();
        for (TodoItem item : items) {
            if (!isCompleted(item) && (today.equals(item.getDueDate()) || MY_DAY_LIST_CODE.equals(item.getListCode()))) {
                todayCount++;
            }
            if (!isCompleted(item) && Boolean.TRUE.equals(item.getImportant())) {
                importantCount++;
            }
            if (isCompleted(item)) {
                completedCount++;
            }
        }
        summary.setTotal(items.size());
        summary.setToday(todayCount);
        summary.setImportant(importantCount);
        summary.setCompleted(completedCount);
        return summary;
    }

    private List<TodoItemSimpleVO> buildUpcoming(List<TodoItem> items) {
        List<TodoItem> candidates = new ArrayList<>();
        for (TodoItem item : items) {
            if (!isCompleted(item) && item.getDueDate() != null) {
                candidates.add(item);
            }
        }
        candidates.sort(Comparator.comparing(TodoItem::getDueDate)
                .thenComparing(TodoItem::getUpdatedAt, Comparator.reverseOrder())
                .thenComparing(TodoItem::getId, Comparator.reverseOrder()));
        List<TodoItemSimpleVO> result = new ArrayList<>();
        int limit = Math.min(UPCOMING_LIMIT, candidates.size());
        for (TodoItem item : candidates.subList(0, limit)) {
            TodoItemSimpleVO vo = new TodoItemSimpleVO();
            vo.setId(item.getId());
            vo.setTitle(item.getTitle());
            vo.setDueDate(item.getDueDate());
            vo.setListCode(item.getListCode());
            result.add(vo);
        }
        return result;
    }

    private List<ListStatVO> buildListStats(List<TodoItem> items) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (DictionaryOptionVO option : dataDictionaryUsageSupport.listEnabledOptionsByUsage(APP_CODE, MODULE_CODE, LIST_CODE_FIELD)) {
            counts.put(option.getItemValue(), 0L);
        }
        for (TodoItem item : items) {
            counts.merge(item.getListCode(), 1L, Long::sum);
        }

        List<ListStatVO> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            ListStatVO vo = new ListStatVO();
            vo.setListCode(entry.getKey());
            vo.setCount(entry.getValue());
            result.add(vo);
        }
        return result;
    }

    private TodoItemListVO toListVO(TodoItem item, List<TodoItemStep> steps) {
        TodoItemListVO vo = new TodoItemListVO();
        vo.setId(item.getId());
        vo.setTitle(item.getTitle());
        vo.setListCode(item.getListCode());
        vo.setImportance(item.getImportance());
        vo.setStatus(item.getStatus());
        vo.setImportant(Boolean.TRUE.equals(item.getImportant()));
        vo.setDueDate(item.getDueDate());
        vo.setReminderAt(item.getReminderAt());
        vo.setNote(item.getNote());
        vo.setSteps(toStepVOs(steps));
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }

    private List<TodoItemStepVO> toStepVOs(List<TodoItemStep> steps) {
        List<TodoItemStepVO> result = new ArrayList<>();
        for (TodoItemStep step : steps) {
            TodoItemStepVO vo = new TodoItemStepVO();
            vo.setId(step.getId());
            vo.setTitle(step.getTitle());
            vo.setDone(Boolean.TRUE.equals(step.getDone()));
            result.add(vo);
        }
        return result;
    }

    private void saveSteps(Long taskId, List<NormalizedStep> steps, LocalDateTime now) {
        for (NormalizedStep step : steps) {
            TodoItemStep entity = new TodoItemStep();
            entity.setTaskId(taskId);
            entity.setTitle(step.title());
            entity.setDone(step.done());
            entity.setSortNo(step.sortNo());
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            todoItemStepMapper.insert(entity);
        }
    }

    private void deleteStepsByTaskIds(List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return;
        }
        QueryWrapper<TodoItemStep> wrapper = new QueryWrapper<>();
        wrapper.in("task_id", taskIds);
        todoItemStepMapper.delete(wrapper);
    }

    private User ensureCurrentUserExists(Long currentUserId) {
        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        return user;
    }

    private TodoItem getOwnedItemOrThrow(Long currentUserId, Long id) {
        QueryWrapper<TodoItem> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).eq("owner_user_id", currentUserId);
        TodoItem item = todoItemMapper.selectOne(wrapper);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "待办任务不存在");
        }
        return item;
    }

    private NormalizedTodo normalizeRequest(SaveTodoItemRequest request) {
        return new NormalizedTodo(
                trimRequired(request.getTitle()),
                normalizeRequiredListCode(request.getListCode()),
                normalizeRequiredImportance(request.getImportance()),
                normalizeRequiredStatus(request.getStatus()),
                Boolean.TRUE.equals(request.getImportant()),
                request.getDueDate(),
                request.getReminderAt(),
                trimToNull(request.getNote()),
                normalizeSteps(request.getSteps())
        );
    }

    private List<NormalizedStep> normalizeSteps(List<TodoItemStepRequest> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<NormalizedStep> result = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            TodoItemStepRequest step = steps.get(i);
            result.add(new NormalizedStep(trimRequired(step.getTitle()), Boolean.TRUE.equals(step.getDone()), i + 1));
        }
        return result;
    }

    private void applyNormalized(TodoItem item, NormalizedTodo normalizedTodo) {
        item.setTitle(normalizedTodo.title());
        item.setListCode(normalizedTodo.listCode());
        item.setImportance(normalizedTodo.importance());
        item.setStatus(normalizedTodo.status());
        item.setImportant(normalizedTodo.important());
        item.setDueDate(normalizedTodo.dueDate());
        item.setReminderAt(normalizedTodo.reminderAt());
        item.setNote(normalizedTodo.note());
    }

    private String normalizeRequiredListCode(String value) {
        return normalizeByUsage(LIST_CODE_FIELD, value, true, "TODO_LIST_CODE_INVALID", "listCode 非法");
    }

    private String normalizeRequiredImportance(String value) {
        return normalizeByUsage(IMPORTANCE_FIELD, value, true, "TODO_IMPORTANCE_INVALID", "importance 非法");
    }

    private String normalizeRequiredStatus(String value) {
        return normalizeByUsage(STATUS_FIELD, value, true, "TODO_STATUS_INVALID", "status 非法");
    }

    private String normalizeOptionalListCode(String value) {
        return normalizeByUsage(LIST_CODE_FIELD, value, false, "TODO_LIST_CODE_INVALID", "listCode 非法");
    }

    private String normalizeOptionalImportance(String value) {
        return normalizeByUsage(IMPORTANCE_FIELD, value, false, "TODO_IMPORTANCE_INVALID", "importance 非法");
    }

    private String normalizeOptionalStatus(String value) {
        return normalizeByUsage(STATUS_FIELD, value, false, "TODO_STATUS_INVALID", "status 非法");
    }

    private String normalizeOptionalViewCode(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return TodoViewCode.ALL.name();
        }
        if (!TodoViewCode.isValid(trimmed)) {
            throw new BusinessException("TODO_VIEW_CODE_INVALID", "viewCode 非法");
        }
        return TodoViewCode.normalize(trimmed);
    }

    private void validateCompletedFlag(Boolean completed, String status) {
        if (completed == null) {
            return;
        }
        boolean isCompletedStatus = COMPLETED_STATUS.equals(status);
        if (completed != isCompletedStatus) {
            throw new BusinessException("TODO_STATUS_MISMATCH", "status 与 completed 语义不一致");
        }
    }

    private static boolean isCompleted(TodoItem item) {
        return COMPLETED_STATUS.equals(item.getStatus());
    }

    private String trimRequired(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException("TODO_REQUIRED_FIELD_MISSING", "请求参数不完整");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean containsIgnoreCase(String source, String needle) {
        return source != null && source.toLowerCase().contains(needle);
    }

    private String normalizeByUsage(String bizFieldCode,
                                    String value,
                                    boolean required,
                                    String errorCode,
                                    String message) {
        try {
            return dataDictionaryUsageSupport.normalizeValueByUsage(APP_CODE, MODULE_CODE, bizFieldCode, value, required);
        } catch (BusinessException exception) {
            throw new BusinessException(errorCode, message);
        }
    }

    private record NormalizedTodo(
            String title,
            String listCode,
            String importance,
            String status,
            boolean important,
            LocalDate dueDate,
            LocalDateTime reminderAt,
            String note,
            List<NormalizedStep> steps
    ) {
    }

    private record NormalizedStep(String title, boolean done, int sortNo) {
    }
}
