package com.gak.todolist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.todolist.domain.TodoItemStep;
import org.apache.ibatis.annotations.Mapper;

/**
 * 待办子任务 Mapper。
 */
@Mapper
public interface TodoItemStepMapper extends BaseMapper<TodoItemStep> {
}
