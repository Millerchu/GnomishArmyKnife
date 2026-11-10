package com.gak.systemlog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

/** 路由模板来自 Spring 的匹配属性，具体路径段不能当作模板。 */
class MatchedRouteTemplateTest {

    @Test
    void keepsTemplateWithoutVariableValues() {
        assertEquals("/reset/{code}", read("/reset/ab", "/reset/{code}", Map.of("code", "ab")));
        assertEquals("/users/{email}", read("/users/a@b.co", "/users/{email}", Map.of("email", "a@b.co")));
        assertEquals("/files/{id}", read("/files/123e4567-e89b-12d3-a456-426614174000", "/files/{id}",
                Map.of("id", "123e4567-e89b-12d3-a456-426614174000")));
        assertEquals("/password-memos/{id}/notes/{note}",
                read("/password-memos/42/notes/short", "/password-memos/{id}/notes/{note}",
                        Map.of("id", "42", "note", "short")));
        assertEquals("/system/logs", read("/system/logs", "/system/logs", Map.of()));
    }

    @Test
    void rejectsOverlongTemplateBeforeSanitizer() {
        String exact = "/" + "a".repeat(254);
        assertEquals(exact, read(exact, exact, Map.of()));
        String overlong = exact + "a";
        assertNull(read(overlong, overlong, Map.of()));
    }

    @Test
    void dropsUnresolvedOrConcretePaths() {
        MockHttpServletRequest bare = org.mockito.Mockito.spy(new MockHttpServletRequest("GET", "/reset/ab"));
        assertNull(MatchedRouteTemplate.read(bare));
        verify(bare, never()).getRequestURI();

        assertNull(read("/reset/ab", "/reset/ab", Map.of("code", "ab")));
        assertNull(read("/users/a@b.co", "/users/a@b.co", Map.of()));
        MockHttpServletRequest patternOnly = new MockHttpServletRequest("GET", "/files/123e4567-e89b-12d3-a456-426614174000");
        patternOnly.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/files/{id}");
        assertNull(MatchedRouteTemplate.read(patternOnly));
    }

    private String read(String uri, String template, Map<String, String> variables) {
        MockHttpServletRequest request = org.mockito.Mockito.spy(new MockHttpServletRequest("GET", uri));
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, template);
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, variables);
        String result = MatchedRouteTemplate.read(request);
        verify(request, never()).getRequestURI();
        return result;
    }
}
