package com.gak.workbench.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gak.workbench.exception.WorkbenchPreferenceInvalidException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 纯文档转换：只保留白名单字段，不修改调用方的 JSON 树。 */
@Component
public class WorkbenchPayloadNormalizer {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_PAYLOAD_BYTES = 16384;
    private static final int GRID_COLUMNS = 12;
    private static final int GRID_ROWS = 48;
    private static final int MAX_DOCK_ITEMS = 100;
    private static final int MAX_DOCK_KEY_LENGTH = 64;
    private static final Set<String> CARD_IDS = Set.of("todo", "workLog", "bills", "health", "fuel", "knowledge");
    private static final Map<String, Dimensions> CARD_SIZES = Map.of(
            "S", new Dimensions(4, 2), "M", new Dimensions(6, 3), "L", new Dimensions(8, 4));

    /** 无历史 schema 定义时不猜测旧结构；新增版本时在此加入逐级迁移，保持无副作用。 */
    public JsonNode upgrade(JsonNode payload, int from) {
        require(from <= CURRENT_SCHEMA_VERSION, "偏好格式版本较新，请更新客户端或升级服务端后重试");
        require(from == CURRENT_SCHEMA_VERSION, "不支持该历史 schemaVersion，请更新客户端");
        require(payload != null && payload.isObject(), "payload 必须为对象");
        return payload.deepCopy();
    }

    /** 坐标以规范化后的档位尺寸校验，避免接受保存后越界的布局。 */
    public ObjectNode normalize(JsonNode payload, int schemaVersion) {
        JsonNode upgraded = upgrade(payload, schemaVersion);
        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        normalized.set("cards", normalizeCards(upgraded.get("cards")));
        normalized.set("dock", normalizeDock(upgraded.get("dock")));
        require(normalized.toString().getBytes(StandardCharsets.UTF_8).length <= MAX_PAYLOAD_BYTES,
                "规范化后的 payload 不能超过 16384 字节");
        return normalized;
    }

    /** 已存文档不能阻断读取版本号；未来格式或无法安全处理的整体文档留给客户端显式覆盖。 */
    public ObjectNode normalizeStored(JsonNode payload, int schemaVersion) {
        if (schemaVersion != CURRENT_SCHEMA_VERSION || payload == null || !payload.isObject()) {
            return null;
        }
        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        normalized.set("cards", normalizeStoredCards(payload.get("cards")));
        normalized.set("dock", normalizeStoredDock(payload.get("dock")));
        return normalized.toString().getBytes(StandardCharsets.UTF_8).length <= MAX_PAYLOAD_BYTES ? normalized : null;
    }

    private ArrayNode normalizeStoredCards(JsonNode cards) {
        ArrayNode normalized = JsonNodeFactory.instance.arrayNode();
        if (cards == null || !cards.isArray()) {
            return normalized;
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode card : cards) {
            if (!card.isObject() || !card.path("id").isTextual()) {
                continue;
            }
            String id = card.get("id").textValue();
            if (!CARD_IDS.contains(id) || seen.contains(id)) {
                continue;
            }
            try {
                normalized.add(normalizeCard(card, id));
                seen.add(id);
            } catch (WorkbenchPreferenceInvalidException ignored) {
                // 只丢弃不可用卡片，其余布局仍可恢复；无效的同 id 项不占用有效卡片位置。
            }
        }
        return normalized;
    }

    private ObjectNode normalizeStoredDock(JsonNode dock) {
        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        normalized.put("collapsed", dock != null && dock.isObject() && dock.path("collapsed").isBoolean()
                && dock.get("collapsed").booleanValue());
        ArrayNode order = normalized.putArray("order");
        JsonNode keys = dock != null && dock.isObject() ? dock.get("order") : null;
        if (keys == null || !keys.isArray()) {
            return normalized;
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode key : keys) {
            if (!key.isTextual()) {
                continue;
            }
            String value = key.textValue().strip();
            if (!value.isEmpty() && value.length() <= MAX_DOCK_KEY_LENGTH && isJsonbText(value) && seen.add(value)) {
                order.add(value);
                if (order.size() == MAX_DOCK_ITEMS) {
                    break;
                }
            }
        }
        return normalized;
    }

    private ArrayNode normalizeCards(JsonNode cards) {
        ArrayNode normalized = JsonNodeFactory.instance.arrayNode();
        if (cards == null) {
            return normalized;
        }
        require(cards.isArray() && cards.size() <= CARD_IDS.size(), "cards 必须为数组且最多 6 张");
        Set<String> seen = new HashSet<>();
        for (JsonNode card : cards) {
            require(card.isObject(), "card 必须为对象");
            String id = text(card.get("id"), "card.id");
            require(CARD_IDS.contains(id), "card.id 不在白名单中");
            require(seen.add(id), "cards 不允许重复 id");
            normalized.add(normalizeCard(card, id));
        }
        return normalized;
    }

    private ObjectNode normalizeCard(JsonNode card, String id) {
        String size = text(card.get("size"), "card.size");
        Dimensions dimensions = CARD_SIZES.get(size);
        require(dimensions != null, "card.size 只接受 S、M、L");
        int x = integer(card.get("x"), "card.x");
        int y = integer(card.get("y"), "card.y");
        // 即使被档位覆盖，也必须是整数，避免静默接受字符串或小数。
        integer(card.get("w"), "card.w");
        integer(card.get("h"), "card.h");
        require(x >= 0 && x <= GRID_COLUMNS - dimensions.width(), "卡片横坐标越界");
        require(y >= 0 && y <= GRID_ROWS - dimensions.height(), "卡片纵坐标越界");
        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        normalized.put("id", id);
        normalized.put("hidden", bool(card.get("hidden"), "card.hidden"));
        normalized.put("size", size);
        normalized.put("x", x);
        normalized.put("y", y);
        normalized.put("w", dimensions.width());
        normalized.put("h", dimensions.height());
        return normalized;
    }

    private ObjectNode normalizeDock(JsonNode dock) {
        require(dock == null || dock.isObject(), "dock 必须为对象");
        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        JsonNode collapsed = dock == null ? null : dock.get("collapsed");
        normalized.put("collapsed", collapsed != null && bool(collapsed, "dock.collapsed"));
        ArrayNode order = normalized.putArray("order");
        JsonNode keys = dock == null ? null : dock.get("order");
        if (keys == null) {
            return normalized;
        }
        require(keys.isArray() && keys.size() <= MAX_DOCK_ITEMS, "dock.order 必须为数组且最多 100 项");
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode key : keys) {
            String value = text(key, "dock.order 元素").strip();
            require(value.length() <= MAX_DOCK_KEY_LENGTH, "dock.order 元素最多 64 字符");
            require(isJsonbText(value), "dock.order 元素包含 JSONB 不支持的字符");
            if (!value.isEmpty() && seen.add(value)) {
                order.add(value);
            }
        }
        return normalized;
    }

    /** JSONB 不接受 NUL 或孤立代理字符，应在入库前作为参数错误拒绝。 */
    private boolean isJsonbText(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\0' || Character.isLowSurrogate(character)) {
                return false;
            }
            if (Character.isHighSurrogate(character)) {
                index++;
                if (index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) {
                    return false;
                }
            }
        }
        return true;
    }

    private String text(JsonNode value, String name) {
        require(value != null && value.isTextual(), name + " 必须为字符串");
        return value.textValue();
    }

    private int integer(JsonNode value, String name) {
        require(value != null && value.isIntegralNumber() && value.canConvertToInt(), name + " 必须为整数");
        return value.intValue();
    }

    private boolean bool(JsonNode value, String name) {
        require(value != null && value.isBoolean(), name + " 必须为布尔值");
        return value.booleanValue();
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new WorkbenchPreferenceInvalidException(message);
        }
    }

    private record Dimensions(int width, int height) { }
}
