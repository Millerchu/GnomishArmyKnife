package com.gak.systemlog.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 只接受 Spring MVC 已经写入的匹配结果：路由模板字符串，以及模板变量表。
 * 变量表里的实值只用于核对模板是否被换成了具体路径，本身不返回、不记录。
 * 没有成对的匹配结果时返回 null，调用方不得再读取 requestURI。
 */
public final class MatchedRouteTemplate {

    static final int MAX_LENGTH = 255;

    private MatchedRouteTemplate() {
    }

    public static String read(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object patternAttribute = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        Object variablesAttribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(patternAttribute instanceof String pattern) || !(variablesAttribute instanceof Map<?, ?> variables)) {
            return null;
        }
        String template = stripControls(pattern).trim();
        if (template.isEmpty() || template.length() > MAX_LENGTH || template.charAt(0) != '/') {
            return null;
        }
        Set<String> names = placeholderNames(template);
        if (names == null || !names.equals(variableKeys(variables, template))) {
            return null;
        }
        return template;
    }

    /**
     * @return 模板变量名；模板不合法时返回 null。发现某个变量实值已经出现在路径段里时也返回 null。
     */
    private static Set<String> variableKeys(Map<?, ?> variables, String template) {
        Set<String> keys = new LinkedHashSet<>();
        for (Map.Entry<?, ?> entry : variables.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !isVariableName(key)) {
                return null;
            }
            if (!(entry.getValue() instanceof String value)) {
                return null;
            }
            if (embedsValue(template, value)) {
                return null;
            }
            keys.add(key);
        }
        return keys;
    }

    private static Set<String> placeholderNames(String template) {
        Set<String> names = new LinkedHashSet<>();
        StringBuilder literal = new StringBuilder();
        int index = 0;
        while (index < template.length()) {
            char current = template.charAt(index);
            if (current == '{') {
                if (!isLiteral(literal)) {
                    return null;
                }
                literal.setLength(0);
                int close = template.indexOf('}', index + 1);
                if (close < 0) {
                    return null;
                }
                String name = variableName(template.substring(index + 1, close));
                if (name == null) {
                    return null;
                }
                names.add(name);
                index = close + 1;
                continue;
            }
            if (current == '}') {
                return null;
            }
            literal.append(current);
            index++;
        }
        if (!isLiteral(literal)) {
            return null;
        }
        return names;
    }

    private static String variableName(String body) {
        String trimmed = body.trim();
        int colon = trimmed.indexOf(':');
        String name = colon >= 0 ? trimmed.substring(0, colon).trim() : trimmed;
        if (name.startsWith("*")) {
            name = name.substring(1);
        }
        return isVariableName(name) ? name : null;
    }

    private static boolean isVariableName(String name) {
        if (name.isEmpty() || (!Character.isLetter(name.charAt(0)) && name.charAt(0) != '_')) {
            return false;
        }
        for (int index = 1; index < name.length(); index++) {
            char current = name.charAt(index);
            if (!Character.isLetterOrDigit(current) && current != '_' && current != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean isLiteral(CharSequence text) {
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '/' || Character.isLetterOrDigit(current) || current == '.' || current == '_'
                    || current == '-' || current == '~' || current == '*') {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean embedsValue(String template, String value) {
        if (value.isEmpty()) {
            return false;
        }
        int start = 0;
        for (int index = 0; index <= template.length(); index++) {
            if (index == template.length() || template.charAt(index) == '/') {
                if (index > start && template.substring(start, index).equals(value)) {
                    return true;
                }
                start = index + 1;
            }
        }
        return false;
    }

    private static String stripControls(String value) {
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
