package com.gak.todolist.controller;

import com.gak.framework.response.ApiResponse;
import com.gak.todolist.dto.SaveTodoItemRequest;
import com.gak.todolist.dto.TodoItemQueryRequest;
import com.gak.todolist.dto.UpdateTodoImportantRequest;
import com.gak.todolist.dto.UpdateTodoStatusRequest;
import com.gak.todolist.service.TodoItemService;
import com.gak.todolist.vo.TodoItemListVO;
import com.gak.todolist.vo.TodoItemPageVO;
import com.gak.user.service.user.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 待办任务控制器。
 */
@RestController
@RequestMapping("/todo-items")
public class TodoItemController {

    private final TodoItemService todoItemService;
    private final TokenService tokenService;

    public TodoItemController(TodoItemService todoItemService, TokenService tokenService) {
        this.todoItemService = todoItemService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResponse<TodoItemPageVO> page(@Valid TodoItemQueryRequest request, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(todoItemService.page(currentUserId, request));
    }

    @PostMapping
    public ApiResponse<TodoItemListVO> create(@Valid @RequestBody SaveTodoItemRequest request,
                                              HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(todoItemService.create(currentUserId, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<TodoItemListVO> update(@PathVariable Long id,
                                              @Valid @RequestBody SaveTodoItemRequest request,
                                              HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        return ApiResponse.success(todoItemService.update(currentUserId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        todoItemService.delete(currentUserId, id);
        return ApiResponse.success();
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody UpdateTodoStatusRequest request,
                                          HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        todoItemService.updateStatus(currentUserId, id, request);
        return ApiResponse.success();
    }

    @PatchMapping("/{id}/important")
    public ApiResponse<Void> updateImportant(@PathVariable Long id,
                                             @Valid @RequestBody UpdateTodoImportantRequest request,
                                             HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        todoItemService.updateImportant(currentUserId, id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/completed")
    public ApiResponse<Void> clearCompleted(HttpServletRequest httpServletRequest) {
        Long currentUserId = tokenService.requireCurrentUserId(httpServletRequest);
        todoItemService.clearCompleted(currentUserId);
        return ApiResponse.success();
    }
}
