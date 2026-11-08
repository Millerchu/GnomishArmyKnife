package com.gak.systemlog.service;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 落库前的唯一脱敏口。异常消息正文不可靠，可能夹带口令、令牌或业务内容，因此直接省略，
 * 只保留异常类名和调用位置。
 */
public class SystemExceptionSanitizer {

    public static final String TRUNCATION_MARKER = "...[truncated]";

    static final int SUMMARY_MAX = 500;
    static final int STACK_MAX = 4000;
    static final int PATH_MAX = 255;
    static final int TYPE_MAX = 255;
    static final int METHOD_MAX = 16;
    static final int MAX_FRAMES = 30;
    static final int MAX_CAUSES = 8;

    private static final Pattern HTTP_METHOD = Pattern.compile("[A-Za-z]{1,16}");

    public SanitizedSystemException sanitize(Throwable exception, String httpMethod, String requestPath) {
        String exceptionType = limit(exception.getClass().getName(), TYPE_MAX);
        String location = location(exception);
        String summary = location == null ? exceptionType : exceptionType + " at " + location;
        String stackTrace = stack(exception);
        boolean stackTruncated = stackTrace.endsWith(TRUNCATION_MARKER);
        return new SanitizedSystemException(
                exceptionType,
                limit(summary, SUMMARY_MAX),
                stackTrace,
                stackTruncated,
                method(httpMethod),
                routeTemplate(requestPath));
    }

    /**
     * 这里只收已经判定过的路由模板。带查询串、片段或邮箱符号的字符串直接丢弃。
     */
    private String routeTemplate(String template) {
        if (template == null || template.isBlank()) {
            return null;
        }
        String cleaned = stripControls(template).trim();
        if (cleaned.isEmpty() || cleaned.indexOf('?') >= 0 || cleaned.indexOf('#') >= 0 || cleaned.indexOf('@') >= 0) {
            return null;
        }
        return limit(cleaned, PATH_MAX);
    }

    private String stack(Throwable exception) {
        StringBuilder builder = new StringBuilder();
        boolean truncated = false;
        int framesUsed = 0;
        int causes = 0;
        Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Throwable current = exception;
        while (current != null) {
            if (!seen.add(current) || causes > MAX_CAUSES) {
                truncated = true;
                break;
            }
            String header = causes == 0
                    ? limit(current.getClass().getName(), TYPE_MAX)
                    : "Caused by: " + limit(current.getClass().getName(), TYPE_MAX);
            if (!appendLine(builder, header)) {
                truncated = true;
                break;
            }
            for (StackTraceElement frame : current.getStackTrace()) {
                if (framesUsed >= MAX_FRAMES) {
                    truncated = true;
                    break;
                }
                if (!appendLine(builder, "\tat " + frameText(frame))) {
                    truncated = true;
                    break;
                }
                framesUsed++;
            }
            if (truncated) {
                break;
            }
            current = current.getCause();
            causes++;
        }
        if (truncated) {
            appendMarker(builder);
        }
        return builder.toString();
    }

    private boolean appendLine(StringBuilder builder, String line) {
        String cleaned = stripControls(line);
        int extra = builder.length() == 0 ? cleaned.length() : cleaned.length() + 1;
        int markerRoom = TRUNCATION_MARKER.length() + 1;
        if (builder.length() + extra + markerRoom > STACK_MAX) {
            return false;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(cleaned);
        return true;
    }

    private void appendMarker(StringBuilder builder) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(TRUNCATION_MARKER);
    }

    private String location(Throwable exception) {
        StackTraceElement[] frames = exception.getStackTrace();
        StackTraceElement chosen = null;
        for (StackTraceElement frame : frames) {
            if (frame.getClassName() != null && frame.getClassName().startsWith("com.gak.")) {
                chosen = frame;
                break;
            }
        }
        if (chosen == null && frames.length > 0) {
            chosen = frames[0];
        }
        return chosen == null ? null : frameText(chosen);
    }

    private String frameText(StackTraceElement element) {
        String className = element.getClassName() == null ? "Unknown" : element.getClassName();
        String methodName = element.getMethodName() == null ? "unknown" : element.getMethodName();
        String location;
        if (element.isNativeMethod()) {
            location = "Native Method";
        } else if (element.getFileName() == null) {
            location = "Unknown Source";
        } else {
            location = element.getFileName() + ":" + element.getLineNumber();
        }
        return stripControls(className + "." + methodName + "(" + location + ")");
    }

    private String method(String httpMethod) {
        if (httpMethod == null) {
            return null;
        }
        String candidate = stripControls(httpMethod).trim();
        if (!HTTP_METHOD.matcher(candidate).matches() || candidate.length() > METHOD_MAX) {
            return null;
        }
        return candidate.toUpperCase(java.util.Locale.ROOT);
    }

    private String limit(String value, int max) {
        String cleaned = stripControls(value);
        if (cleaned.length() <= max) {
            return cleaned;
        }
        int keep = max - TRUNCATION_MARKER.length();
        if (keep <= 0) {
            return TRUNCATION_MARKER.substring(0, Math.min(max, TRUNCATION_MARKER.length()));
        }
        return cleaned.substring(0, keep) + TRUNCATION_MARKER;
    }

    private String stripControls(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current >= 0x20 && current != 0x7F) {
                builder.append(current);
            }
        }
        return builder.toString();
    }
}
