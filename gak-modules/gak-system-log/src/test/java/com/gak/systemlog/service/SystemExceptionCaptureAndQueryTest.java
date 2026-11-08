package com.gak.systemlog.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gak.framework.exception.BusinessException;
import com.gak.framework.exception.SystemExceptionCapture;
import com.gak.systemlog.domain.SystemExceptionLog;
import com.gak.systemlog.dto.SystemExceptionLogQuery;
import com.gak.systemlog.mapper.SystemExceptionLogMapper;
import com.gak.systemlog.vo.SystemExceptionLogDetailVO;
import com.gak.systemlog.vo.SystemExceptionLogItemVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/** 采集失败、后台空请求字段、容量不删除，以及查询权限和筛选列。 */
class SystemExceptionCaptureAndQueryTest {

    private PersistentSystemExceptionCapture capture;

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
        if (capture != null) {
            capture.shutdown();
        }
    }

    @Test
    void httpCaptureStoresSanitizedPathOnly() throws Exception {
        SystemExceptionLogWriter writer = mock(SystemExceptionLogWriter.class);
        capture = newCapture(writer);
        HttpServletRequest request = spyRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        capture.capture(secretFailure(), SystemExceptionCapture.SOURCE_HTTP);

        SystemExceptionLog stored = captured(writer);
        assertEquals("HTTP", stored.getSource());
        assertTrue(stored.getTraceId().matches("[0-9a-f]{32}"));
        assertEquals("GET", stored.getHttpMethod());
        assertEquals("/reset/{code}", stored.getRequestPath());
        assertFalse(stored.getRequestPath().contains("ab"));
        assertFalse(stored.getSummary().contains("memo-secret"));
        assertFalse(stored.getStackTrace().contains("memo-secret"));
        verify(request, never()).getRequestURI();
        verify(request, never()).getHeader(any());
        verify(request, never()).getInputStream();
        verify(request, never()).getReader();
        verify(request, never()).getParameterMap();
        verify(request, never()).getCookies();
        verify(request, never()).getRemoteAddr();
    }

    @Test
    void backgroundCaptureLeavesRequestFieldsNull() {
        SystemExceptionLogWriter writer = mock(SystemExceptionLogWriter.class);
        capture = newCapture(writer);
        MockHttpServletRequest request = org.mockito.Mockito.spy(new MockHttpServletRequest("POST", "/password-memos/ab"));
        request.addHeader("Authorization", "Bearer abc");
        request.setQueryString("token=abc");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/password-memos/{id}");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                java.util.Map.of("id", "ab"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        capture.capture(secretFailure(), SystemExceptionCapture.SOURCE_ASYNC_TASK);

        SystemExceptionLog stored = captured(writer);
        assertEquals("ASYNC_TASK", stored.getSource());
        assertNull(stored.getHttpMethod());
        assertNull(stored.getRequestPath());
        assertFalse(stored.getSummary().contains("memo-secret"));
        assertFalse(String.valueOf(stored.getRequestPath()).contains("ab"));
        verify(request, never()).getRequestURI();
    }

    @Test
    void dynamicPathValuesAreNotPersisted() {
        persistTemplate("/reset/ab", "/reset/{code}", java.util.Map.of("code", "ab"), "ab");
        persistTemplate("/users/a@b.co", "/users/{email}", java.util.Map.of("email", "a@b.co"), "a@b.co");
        String identifier = "123e4567-e89b-12d3-a456-426614174000";
        persistTemplate("/files/" + identifier, "/files/{id}", java.util.Map.of("id", identifier), identifier);
        persistTemplate("/password-memos/42/notes/short", "/password-memos/{id}/notes/{note}",
                java.util.Map.of("id", "42", "note", "short"), "short");
        persistTemplate("/password-memos/42/notes/short", "/password-memos/{id}/notes/{note}",
                java.util.Map.of("id", "42", "note", "short"), "42");

        SystemExceptionLogWriter writer = mock(SystemExceptionLogWriter.class);
        capture = newCapture(writer);
        MockHttpServletRequest unresolved = org.mockito.Mockito.spy(new MockHttpServletRequest("GET", "/reset/ab"));
        unresolved.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/reset/ab");
        unresolved.setAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                java.util.Map.of("code", "ab"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(unresolved));
        capture.capture(secretFailure(), SystemExceptionCapture.SOURCE_HTTP);
        assertNull(captured(writer).getRequestPath());
        verify(unresolved, never()).getRequestURI();

        SystemExceptionLogWriter missingHandler = mock(SystemExceptionLogWriter.class);
        capture.shutdown();
        capture = newCapture(missingHandler);
        MockHttpServletRequest bare = org.mockito.Mockito.spy(new MockHttpServletRequest("GET", "/users/a@b.co"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(bare));
        capture.capture(secretFailure(), SystemExceptionCapture.SOURCE_HTTP);
        assertNull(captured(missingHandler).getRequestPath());
        verify(bare, never()).getRequestURI();
    }

    private void persistTemplate(String uri, String template, java.util.Map<String, String> variables, String secret) {
        if (capture != null) {
            capture.shutdown();
        }
        SystemExceptionLogWriter writer = mock(SystemExceptionLogWriter.class);
        capture = newCapture(writer);
        MockHttpServletRequest request = org.mockito.Mockito.spy(new MockHttpServletRequest("GET", uri));
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, template);
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, variables);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        capture.capture(secretFailure(), SystemExceptionCapture.SOURCE_HTTP);
        SystemExceptionLog stored = captured(writer);
        assertEquals(template, stored.getRequestPath());
        assertFalse(stored.getRequestPath().contains(secret), secret);
        assertFalse(stored.getSummary().contains(secret), secret);
        assertFalse(stored.getStackTrace().contains(secret), secret);
        verify(request, never()).getRequestURI();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void writeFailureAndReentryDoNotEscape() {
        SystemExceptionLogWriter writer = mock(SystemExceptionLogWriter.class);
        capture = newCapture(writer);
        doThrow(new IllegalStateException("database unavailable")).when(writer).insert(any());
        assertDoesNotThrow(() -> capture.capture(secretFailure(), SystemExceptionCapture.SOURCE_HTTP));

        AtomicInteger inserts = new AtomicInteger();
        doAnswer(invocation -> {
            if (inserts.incrementAndGet() == 1) {
                capture.capture(secretFailure(), SystemExceptionCapture.SOURCE_SCHEDULER);
            }
            return null;
        }).when(writer).insert(any());
        assertDoesNotThrow(() -> capture.capture(secretFailure(), SystemExceptionCapture.SOURCE_HTTP));
        assertEquals(1, inserts.get());
    }

    @Test
    void capacitySkipsInsertWithoutDeletingHistory() {
        SystemExceptionLogMapper mapper = mock(SystemExceptionLogMapper.class);
        when(mapper.selectCount(any())).thenReturn(20L);
        SystemExceptionLogWriter writer = new SystemExceptionLogWriter(mapper, 20, 30);
        writer.insert(new SystemExceptionLog());
        verify(mapper, never()).insert(any(SystemExceptionLog.class));
        verify(mapper, never()).deleteById(any(java.io.Serializable.class));
        verify(mapper, never()).deleteById(any(SystemExceptionLog.class));
        verify(mapper, never()).delete(any());
        verify(mapper, never()).deleteByMap(any());

        when(mapper.selectCount(any())).thenReturn(19L);
        SystemExceptionLog row = new SystemExceptionLog();
        writer.insert(row);
        verify(mapper).insert(row);
    }

    @Test
    void ordinaryUserCannotReadAndKeywordSearchesSummaryOnly() {
        SystemExceptionLogMapper logs = mock(SystemExceptionLogMapper.class);
        UserMapper users = mock(UserMapper.class);
        SystemExceptionLogService service = new SystemExceptionLogService(logs, users);
        when(users.selectById(2L)).thenReturn(user("USER", true, "ENABLED"));
        SystemExceptionLogQuery query = new SystemExceptionLogQuery();
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.page(2L, query)).getStatusCode().value());
        verify(logs, never()).hasEnabledGrant(any(), any());
        verify(logs, never()).selectList(any());

        when(users.selectById(1L)).thenReturn(user("ADMIN", true, "ENABLED"));
        when(logs.hasEnabledGrant(1L, "APP_SYSTEM_LOG")).thenReturn(false);
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.detail(1L, 9L)).getStatusCode().value());

        when(users.selectById(3L)).thenReturn(user("ADMIN", false, "ENABLED"));
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.page(3L, query)).getStatusCode().value());

        when(logs.hasEnabledGrant(1L, "APP_SYSTEM_LOG")).thenReturn(true);
        query.setOccurredFrom(LocalDateTime.of(2026, 11, 7, 20, 0, 0));
        query.setOccurredTo(LocalDateTime.of(2026, 11, 7, 18, 0, 0));
        assertThrows(BusinessException.class, () -> service.page(1L, query));

        query.setOccurredTo(LocalDateTime.of(2026, 11, 7, 21, 0, 0));
        query.setExceptionType("java.lang.IllegalStateException");
        query.setTraceId("abc123");
        query.setKeyword("100%_");
        ArgumentCaptor<QueryWrapper<SystemExceptionLog>> countCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        ArgumentCaptor<QueryWrapper<SystemExceptionLog>> listCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        when(logs.selectCount(countCaptor.capture())).thenReturn(1L);
        SystemExceptionLog stored = storedRow();
        when(logs.selectList(listCaptor.capture())).thenReturn(List.of(stored));

        var page = service.page(1L, query);
        SystemExceptionLogItemVO item = page.list().get(0);
        assertEquals(1L, page.total());
        assertEquals("safe summary", item.summary());
        assertNull(item.requestPath());
        assertFalse(listCaptor.getValue().getSqlSelect().contains("stack_trace"));
        String filters = listCaptor.getValue().getSqlSegment();
        assertTrue(filters.contains("summary ILIKE"));
        assertTrue(filters.contains("exception_type"));
        assertTrue(filters.contains("trace_id"));
        assertTrue(filters.contains("occurred_at"));
        assertFalse(filters.contains("stack_trace"));
        assertTrue(listCaptor.getValue().getParamNameValuePairs().containsValue("%100\\%\\_%"));
        assertFalse(countCaptor.getValue().getSqlSegment().contains("LIMIT"));

        when(logs.selectById(8L)).thenReturn(stored);
        SystemExceptionLogDetailVO detail = service.detail(1L, 8L);
        assertEquals("class only", detail.stackTrace());
        assertTrue(detail.stackTruncated());
        assertFalse(detail.stackTrace().contains("memo-secret"));
    }

    private PersistentSystemExceptionCapture newCapture(SystemExceptionLogWriter writer) {
        return new PersistentSystemExceptionCapture(new SystemExceptionSanitizer(), writer, inlineExecutor());
    }

    private SystemExceptionLog captured(SystemExceptionLogWriter writer) {
        ArgumentCaptor<SystemExceptionLog> captor = ArgumentCaptor.forClass(SystemExceptionLog.class);
        verify(writer).insert(captor.capture());
        return captor.getValue();
    }

    private HttpServletRequest spyRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("get", "/reset/ab");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/reset/{code}");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                java.util.Map.of("code", "ab"));
        return org.mockito.Mockito.spy(request);
    }

    private IllegalStateException secretFailure() {
        IllegalStateException failure = new IllegalStateException("password=memo-secret");
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("com.gak.demo.Worker", "run", "Worker.java", 3)
        });
        return failure;
    }

    private User user(String role, boolean enabled, String status) {
        User user = new User();
        user.setId(1L);
        user.setRoleCode(role);
        user.setEnabled(enabled);
        user.setStatus(status);
        return user;
    }

    private SystemExceptionLog storedRow() {
        SystemExceptionLog log = new SystemExceptionLog();
        log.setId(8L);
        log.setOccurredAt(LocalDateTime.of(2026, 11, 7, 20, 15, 30));
        log.setTraceId("abc123");
        log.setSource("ASYNC_TASK");
        log.setExceptionType("java.lang.IllegalStateException");
        log.setSummary("safe summary");
        log.setStackTrace("class only");
        log.setStackTruncated(true);
        log.setHttpMethod(null);
        log.setRequestPath(null);
        return log;
    }

    private ExecutorService inlineExecutor() {
        return new AbstractExecutorService() {
            private boolean shutdown;

            @Override
            public void shutdown() {
                shutdown = true;
            }

            @Override
            public List<Runnable> shutdownNow() {
                shutdown = true;
                return List.of();
            }

            @Override
            public boolean isShutdown() {
                return shutdown;
            }

            @Override
            public boolean isTerminated() {
                return shutdown;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return true;
            }

            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
    }
}
