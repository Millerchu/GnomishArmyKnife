package com.gak.systemlog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 脱敏在构造可落库快照时完成，异常消息正文不会进入任何字段。 */
class SystemExceptionSanitizerTest {

    private final SystemExceptionSanitizer sanitizer = new SystemExceptionSanitizer();

    @Test
    void omitsUnreliableMessagesAndKeepsCallSite() {
        IllegalStateException failure = new IllegalStateException("password=memo-secret\nBearer token-value");
        IllegalArgumentException cause = new IllegalArgumentException("raw memo content and cookie=session");
        cause.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("com.gak.demo.Repository", "find", "Repository.java", 7)
        });
        failure.initCause(cause);
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("com.gak.passwordmemo.service.PasswordMemoService", "reveal", "PasswordMemoService.java", 40),
                new StackTraceElement("com.gak.demo.Caller", "call", "Caller.java", 8)
        });

        SanitizedSystemException sanitized = sanitizer.sanitize(failure, "get", "/password-memos/{id}");

        assertEquals("java.lang.IllegalStateException", sanitized.exceptionType());
        assertEquals(
                "java.lang.IllegalStateException at com.gak.passwordmemo.service.PasswordMemoService.reveal(PasswordMemoService.java:40)",
                sanitized.summary());
        assertFalse(sanitized.summary().contains("memo-secret"));
        assertFalse(sanitized.stackTrace().contains("memo-secret"));
        assertFalse(sanitized.stackTrace().contains("Bearer"));
        assertFalse(sanitized.stackTrace().contains("cookie"));
        assertTrue(sanitized.stackTrace().contains("Caused by: java.lang.IllegalArgumentException"));
        assertFalse(sanitized.stackTruncated());
        assertFalse(sanitized.summary().contains("\n"));
        assertEquals("GET", sanitized.httpMethod());
        assertEquals("/password-memos/{id}", sanitized.requestPath());
        assertNull(sanitizer.sanitize(failure, "G@T", "/reset/ab?token=abc").requestPath());
        assertNull(sanitizer.sanitize(failure, "GET", "/users/a@b.co").requestPath());
    }

    @Test
    void marksTruncatedStack() {
        IllegalStateException failure = new IllegalStateException("password=secret");
        StackTraceElement[] frames = new StackTraceElement[31];
        for (int index = 0; index < frames.length; index++) {
            frames[index] = new StackTraceElement("com.gak.demo.Worker", "run", "Worker.java", index + 1);
        }
        failure.setStackTrace(frames);

        SanitizedSystemException sanitized = sanitizer.sanitize(failure, null, null);

        assertTrue(sanitized.stackTruncated());
        assertTrue(sanitized.stackTrace().endsWith(SystemExceptionSanitizer.TRUNCATION_MARKER));
        assertFalse(sanitized.stackTrace().contains("password=secret"));
        assertTrue(sanitized.stackTrace().length() <= 4000);
        assertNull(sanitized.httpMethod());
        assertNull(sanitized.requestPath());
    }
}
