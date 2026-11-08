package com.gak.systemlog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.response.PagedResult;
import com.gak.systemlog.domain.SystemExceptionLog;
import com.gak.systemlog.dto.SystemExceptionLogQuery;
import com.gak.systemlog.mapper.SystemExceptionLogMapper;
import com.gak.systemlog.vo.SystemExceptionLogDetailVO;
import com.gak.systemlog.vo.SystemExceptionLogItemVO;
import com.gak.user.constant.UserSecurityConstants;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 系统异常日志查询。读取权限同时要求管理员角色和已启用的应用授权。
 */
@Service
public class SystemExceptionLogService {

    static final String APP_CODE = "APP_SYSTEM_LOG";
    private static final String QUERY_INVALID = "SYSTEM_LOG_QUERY_INVALID";
    private static final int MAX_PAGE_SIZE = 100;

    private final SystemExceptionLogMapper logMapper;
    private final UserMapper userMapper;

    public SystemExceptionLogService(SystemExceptionLogMapper logMapper, UserMapper userMapper) {
        this.logMapper = logMapper;
        this.userMapper = userMapper;
    }

    public PagedResult<SystemExceptionLogItemVO> page(Long userId, SystemExceptionLogQuery query) {
        requireReader(userId);
        if (query == null) {
            throw new BusinessException(QUERY_INVALID, "查询条件不能为空");
        }
        validateRange(query);
        validatePage(query);
        QueryWrapper<SystemExceptionLog> countWrapper = new QueryWrapper<>();
        applyFilters(countWrapper, query);
        Long totalValue = logMapper.selectCount(countWrapper);
        long total = totalValue == null ? 0L : totalValue;
        QueryWrapper<SystemExceptionLog> listWrapper = new QueryWrapper<>();
        listWrapper.select("id", "occurred_at", "trace_id", "source", "exception_type", "summary", "http_method", "request_path");
        applyFilters(listWrapper, query);
        long offset = (long) (query.getPageNo() - 1) * query.getPageSize();
        listWrapper.orderByDesc("occurred_at").orderByDesc("id");
        listWrapper.last("LIMIT " + query.getPageSize() + " OFFSET " + offset);
        List<SystemExceptionLogItemVO> list = logMapper.selectList(listWrapper).stream().map(this::toItem).toList();
        return new PagedResult<>(list, total);
    }

    public SystemExceptionLogDetailVO detail(Long userId, Long id) {
        requireReader(userId);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "系统日志不存在");
        }
        SystemExceptionLog log = logMapper.selectById(id);
        if (log == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "系统日志不存在");
        }
        return toDetail(log);
    }

    private void requireReader(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null
                || !Boolean.TRUE.equals(user.getEnabled())
                || !UserSecurityConstants.ENABLED_STATUS.equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户已失效，请重新登录");
        }
        String roleCode = StringUtils.hasText(user.getRoleCode())
                ? user.getRoleCode()
                : UserSecurityConstants.DEFAULT_ROLE_CODE;
        if (!UserSecurityConstants.ADMIN_ROLE_CODE.equalsIgnoreCase(roleCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以查看系统日志");
        }
        if (!logMapper.hasEnabledGrant(userId, APP_CODE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前管理员未获系统日志应用授权或应用已停用");
        }
    }

    private void validateRange(SystemExceptionLogQuery query) {
        if (query.getOccurredFrom() != null
                && query.getOccurredTo() != null
                && query.getOccurredFrom().isAfter(query.getOccurredTo())) {
            throw new BusinessException(QUERY_INVALID, "开始时间不能晚于结束时间");
        }
    }

    private void validatePage(SystemExceptionLogQuery query) {
        if (query.getPageNo() < 1 || query.getPageSize() < 1 || query.getPageSize() > MAX_PAGE_SIZE) {
            throw new BusinessException(QUERY_INVALID, "分页参数不合法");
        }
    }

    /**
     * 关键字只比较 summary。该列在写入前已经去掉异常消息正文。
     */
    private void applyFilters(QueryWrapper<SystemExceptionLog> wrapper, SystemExceptionLogQuery query) {
        if (query.getOccurredFrom() != null) {
            wrapper.ge("occurred_at", query.getOccurredFrom());
        }
        if (query.getOccurredTo() != null) {
            wrapper.le("occurred_at", query.getOccurredTo());
        }
        if (StringUtils.hasText(query.getExceptionType())) {
            wrapper.eq("exception_type", query.getExceptionType().trim());
        }
        if (StringUtils.hasText(query.getTraceId())) {
            wrapper.eq("trace_id", query.getTraceId().trim());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.apply("summary ILIKE {0} ESCAPE '\\'", likePattern(query.getKeyword().trim()));
        }
    }

    private String likePattern(String keyword) {
        String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private SystemExceptionLogItemVO toItem(SystemExceptionLog log) {
        return new SystemExceptionLogItemVO(
                log.getId(),
                log.getOccurredAt(),
                log.getTraceId(),
                log.getSource(),
                log.getExceptionType(),
                log.getSummary(),
                log.getHttpMethod(),
                log.getRequestPath());
    }

    private SystemExceptionLogDetailVO toDetail(SystemExceptionLog log) {
        return new SystemExceptionLogDetailVO(
                log.getId(),
                log.getOccurredAt(),
                log.getTraceId(),
                log.getSource(),
                log.getExceptionType(),
                log.getSummary(),
                log.getHttpMethod(),
                log.getRequestPath(),
                log.getStackTrace(),
                Boolean.TRUE.equals(log.getStackTruncated()));
    }
}
