package com.gak.todolist.service;

import com.gak.framework.exception.BusinessException;
import com.gak.todolist.domain.TodoItem;
import com.gak.todolist.domain.TodoItemStep;
import com.gak.todolist.dto.SaveTodoItemRequest;
import com.gak.todolist.dto.TodoItemQueryRequest;
import com.gak.todolist.dto.TodoItemStepRequest;
import com.gak.todolist.dto.UpdateTodoImportantRequest;
import com.gak.todolist.dto.UpdateTodoStatusRequest;
import com.gak.todolist.mapper.TodoItemMapper;
import com.gak.todolist.mapper.TodoItemStepMapper;
import com.gak.todolist.vo.TodoItemListVO;
import com.gak.todolist.vo.TodoItemPageVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoItemServiceTest {

    @Mock
    private TodoItemMapper todoItemMapper;

    @Mock
    private TodoItemStepMapper todoItemStepMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private TodoItemService todoItemService;

    @Test
    void createShouldPersistStepsAndReturnStepView() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        doAnswer(invocation -> {
            TodoItem item = invocation.getArgument(0);
            item.setId(11L);
            return 1;
        }).when(todoItemMapper).insert(any(TodoItem.class));
        doAnswer(invocation -> {
            TodoItemStep step = invocation.getArgument(0);
            step.setId((long) step.getSortNo());
            return 1;
        }).when(todoItemStepMapper).insert(any(TodoItemStep.class));
        when(todoItemStepMapper.selectList(any())).thenReturn(List.of(
                buildStep(1L, 11L, "写接口", false, 1),
                buildStep(2L, 11L, "补测试", true, 2)
        ));

        SaveTodoItemRequest request = new SaveTodoItemRequest();
        request.setTitle("开发待办列表接口");
        request.setListCode("WORK");
        request.setImportance("HIGH");
        request.setStatus("TODO");
        request.setImportant(true);
        request.setDueDate(LocalDate.now());
        request.setSteps(List.of(stepRequest("写接口", false), stepRequest("补测试", true)));

        TodoItemListVO result = todoItemService.create(1L, request);

        verify(todoItemMapper).insert(any(TodoItem.class));
        verify(todoItemStepMapper, times(2)).insert(any(TodoItemStep.class));
        assertEquals(2, result.getSteps().size());
        assertEquals("写接口", result.getSteps().get(0).getTitle());
    }

    @Test
    void pageShouldSupportKeywordAndViewSorting() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        when(todoItemMapper.selectList(any())).thenReturn(List.of(
                buildItem(1L, "今天要做", "MY_DAY", "HIGH", "TODO", true, LocalDate.now()),
                buildItem(2L, "普通任务", "WORK", "MEDIUM", "TODO", false, LocalDate.now().plusDays(2)),
                buildItem(3L, "已完成任务", "PERSONAL", "LOW", "COMPLETED", false, LocalDate.now().minusDays(1))
        ));
        when(todoItemStepMapper.selectList(any())).thenReturn(List.of(
                buildStep(10L, 2L, "补文档", false, 1)
        ));

        TodoItemQueryRequest request = new TodoItemQueryRequest();
        request.setPageNo(1L);
        request.setPageSize(10L);
        request.setKeyword("文档");

        TodoItemPageVO result = todoItemService.page(1L, request);

        assertEquals(1, result.getList().size());
        assertEquals("普通任务", result.getList().get(0).getTitle());
        assertEquals(3, result.getSummary().getTotal());
        assertEquals(5, result.getListStats().size());
    }

    @Test
    void updateStatusShouldRejectInconsistentCompletedFlag() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        when(todoItemMapper.selectOne(any())).thenReturn(buildItem(1L, "任务", "WORK", "HIGH", "TODO", false, LocalDate.now()));

        UpdateTodoStatusRequest request = new UpdateTodoStatusRequest();
        request.setStatus("COMPLETED");
        request.setCompleted(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> todoItemService.updateStatus(1L, 1L, request));
        assertEquals("TODO_STATUS_MISMATCH", exception.getCode());
    }

    @Test
    void clearCompletedShouldDeleteCompletedItemsAndSteps() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        when(todoItemMapper.selectList(any())).thenReturn(List.of(
                buildItem(3L, "已完成1", "WORK", "LOW", "COMPLETED", false, null),
                buildItem(4L, "已完成2", "PERSONAL", "LOW", "COMPLETED", false, null)
        ));

        todoItemService.clearCompleted(1L);

        verify(todoItemStepMapper).delete(any());
        verify(todoItemMapper).delete(any());
    }

    @Test
    void updateImportantShouldUpdateFlag() {
        when(userMapper.selectById(1L)).thenReturn(buildUser(1L));
        when(todoItemMapper.selectOne(any())).thenReturn(buildItem(1L, "任务", "WORK", "HIGH", "TODO", false, LocalDate.now()));

        UpdateTodoImportantRequest request = new UpdateTodoImportantRequest();
        request.setImportant(true);
        todoItemService.updateImportant(1L, 1L, request);

        ArgumentCaptor<TodoItem> captor = ArgumentCaptor.forClass(TodoItem.class);
        verify(todoItemMapper).updateById(captor.capture());
        assertEquals(true, captor.getValue().getImportant());
    }

    private User buildUser(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private TodoItem buildItem(Long id,
                               String title,
                               String listCode,
                               String importance,
                               String status,
                               boolean important,
                               LocalDate dueDate) {
        TodoItem item = new TodoItem();
        item.setId(id);
        item.setOwnerUserId(1L);
        item.setTitle(title);
        item.setListCode(listCode);
        item.setImportance(importance);
        item.setStatus(status);
        item.setImportant(important);
        item.setDueDate(dueDate);
        item.setUpdatedAt(java.time.LocalDateTime.now());
        item.setCreatedAt(java.time.LocalDateTime.now());
        return item;
    }

    private TodoItemStep buildStep(Long id, Long taskId, String title, boolean done, int sortNo) {
        TodoItemStep step = new TodoItemStep();
        step.setId(id);
        step.setTaskId(taskId);
        step.setTitle(title);
        step.setDone(done);
        step.setSortNo(sortNo);
        step.setCreatedAt(java.time.LocalDateTime.now());
        step.setUpdatedAt(java.time.LocalDateTime.now());
        return step;
    }

    private TodoItemStepRequest stepRequest(String title, boolean done) {
        TodoItemStepRequest request = new TodoItemStepRequest();
        request.setTitle(title);
        request.setDone(done);
        return request;
    }
}
