package com.gak.todolist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gak.todolist.domain.TodoItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 待办任务 Mapper。
 */
@Mapper
public interface TodoItemMapper extends BaseMapper<TodoItem> {
}
