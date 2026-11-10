package com.gak.systemlog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
    void exactFrameAndCharacterLimits() {
        IllegalStateException failure = new IllegalStateException();
        StackTraceElement[] frames = new StackTraceElement[30];
        java.util.Arrays.fill(frames, new StackTraceElement("C", "m", "F", 1));
        failure.setStackTrace(frames);
        var exactFrames = sanitizer.sanitize(failure, null, null);
        assertFalse(exactFrames.stackTruncated());
        assertEquals(31, exactFrames.stackTrace().lines().count());
        for (int length : new int[] {4000, 4001}) {
            String header = failure.getClass().getName();
            String suffix = ".m(F:1)";
            // 去控制字符后的单帧为 "at " + 类名 + 后缀，另有一字节换行。
            String className = "C".repeat(length - header.length() - 1 - 3 - suffix.length());
            failure.setStackTrace(new StackTraceElement[] {new StackTraceElement(className, "m", "F", 1)});
            var result = sanitizer.sanitize(failure, null, null);
            assertEquals(length > 4000, result.stackTruncated());
            assertEquals(length > 4000, result.stackTrace().endsWith(SystemExceptionSanitizer.TRUNCATION_MARKER));
            assertTrue(result.stackTrace().length() <= 4000);
            if (length == 4000) {
                assertEquals(4000, result.stackTrace().length());
            }
        }
    }

    @Test
    void overflowingStackDropsWholeLastLineIncludingSupplementaryCharacter() {
        IllegalStateException failure = new IllegalStateException();
        String firstFrame = "at C.m(F:1)";
        String prefix = failure.getClass().getName() + "\n" + firstFrame + "\nat ";
        String suffix = ".m(F:1)";
        int retained = SystemExceptionSanitizer.STACK_MAX - SystemExceptionSanitizer.TRUNCATION_MARKER.length() - 1;
        // 让旧实现的截断点落在代理对中间，且该帧原本能完整放进 4000 字符。
        String className = "C".repeat(retained - 1 - prefix.length()) + "\uD83D\uDE00"
                + "C".repeat(4000 - retained - 1 - suffix.length());
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("C", "m", "F", 1),
                new StackTraceElement(className, "m", "F", 1),
                new StackTraceElement("Overflow", "m", "F", 1)
        });

        var result = sanitizer.sanitize(failure, null, null);

        assertEquals(failure.getClass().getName() + "\n" + firstFrame + "\n"
                + SystemExceptionSanitizer.TRUNCATION_MARKER, result.stackTrace());
        assertTrue(result.stackTruncated());
        assertNoIsolatedSurrogates(result.stackTrace());
    }

    @Test
    void oversizedFirstLineUsesSurrogateSafeFallback() {
        int retained = SystemExceptionSanitizer.STACK_MAX - SystemExceptionSanitizer.TRUNCATION_MARKER.length() - 1;
        for (int length : new int[] {3990, 4001, 5000}) {
            String line = "C".repeat(retained - 1) + "\uD83D\uDE00" + "C".repeat(length - retained - 1);
            StringBuilder stack = new StringBuilder();
            // 实际异常头另有 255 字符上限；直接覆盖行拼接器的首行兜底边界。
            boolean appended = ReflectionTestUtils.invokeMethod(sanitizer, "appendLine", stack, line);
            if (appended) {
                assertFalse((boolean) ReflectionTestUtils.invokeMethod(sanitizer, "appendLine", stack, "C".repeat(100)));
            }
            ReflectionTestUtils.invokeMethod(sanitizer, "appendMarker", stack);

            assertEquals("C".repeat(retained - 1) + "\n" + SystemExceptionSanitizer.TRUNCATION_MARKER, stack.toString());
            assertTrue(stack.length() <= SystemExceptionSanitizer.STACK_MAX);
            assertNoIsolatedSurrogates(stack.toString());
        }
    }

    private void assertNoIsolatedSurrogates(String text) {
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (Character.isHighSurrogate(current)) {
                assertTrue(index + 1 < text.length() && Character.isLowSurrogate(text.charAt(index + 1)));
                index++;
            } else {
                assertFalse(Character.isLowSurrogate(current));
            }
        }
    }

    @Test
    void longSummaryAndRouteUseExistingLimits() {
        IllegalStateException failure = new IllegalStateException();
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("com.gak." + "C".repeat(700), "m", "F", 1)});
        var result = sanitizer.sanitize(failure, "GET", "/" + "a".repeat(400));
        assertEquals(500, result.summary().length());
        assertEquals(255, result.requestPath().length());
        assertTrue(result.summary().endsWith(SystemExceptionSanitizer.TRUNCATION_MARKER));
        assertTrue(result.requestPath().endsWith(SystemExceptionSanitizer.TRUNCATION_MARKER));
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
