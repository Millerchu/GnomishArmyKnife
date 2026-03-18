package com.gak.framework.response;

import java.util.List;

/**
 * 分页数据结构。
 *
 * @param list 列表数据
 * @param total 总数
 * @param <T> 元素类型
 */
public record PagedResult<T>(List<T> list, long total) {
}
