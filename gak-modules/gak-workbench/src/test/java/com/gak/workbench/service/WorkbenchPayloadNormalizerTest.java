package com.gak.workbench.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gak.workbench.exception.WorkbenchPreferenceInvalidException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** 校验规范化后的语义、字节上限及纯函数约束。 */
class WorkbenchPayloadNormalizerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final WorkbenchPayloadNormalizer normalizer = new WorkbenchPayloadNormalizer();
    private static final String CARD = """
            {"id":"todo","hidden":false,"size":"S","x":0,"y":0,"w":99,"h":99,"future":true}
            """;

    @Test
    void dropsUnknownFieldsNormalizesSizesAndPreservesHistoricalDockKeys() throws Exception {
        JsonNode source = json.readTree("""
                {"cards":[%s],"dock":{"collapsed":true,"order":[" x ","","x","old-unauthorized"],
                "future":true},"future":{"secret":true}}
                """.formatted(CARD));
        JsonNode before = source.deepCopy();
        JsonNode result = normalizer.normalize(source, 1);
        assertEquals(json.readTree("""
                {"cards":[{"id":"todo","hidden":false,"size":"S","x":0,"y":0,"w":4,"h":2}],
                "dock":{"collapsed":true,"order":["x","old-unauthorized"]}}
                """), result);
        assertEquals(before, source);
        assertEquals(result, normalizer.normalize(result, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"S,4,2", "M,6,3", "L,8,4"})
    void normalizesEverySizeAndAcceptsExactGridEdges(String setting) throws Exception {
        String[] parts = setting.split(",");
        int width = Integer.parseInt(parts[1]);
        int height = Integer.parseInt(parts[2]);
        ObjectNode card = (ObjectNode) json.readTree(CARD);
        card.put("size", parts[0]).put("x", 12 - width).put("y", 48 - height);
        JsonNode result = normalizer.normalize(json.createObjectNode().set("cards", json.createArrayNode().add(card)), 1);
        assertEquals(width, result.at("/cards/0/w").asInt());
        assertEquals(height, result.at("/cards/0/h").asInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "1", "\"text\"", "{\"cards\":null}", "{\"cards\":{}}",
            "{\"dock\":null}", "{\"dock\":{\"collapsed\":1}}", "{\"dock\":{\"order\":[1]}}"})
    void rejectsInvalidPayloadShapes(String source) throws Exception {
        JsonNode payload = json.readTree(source);
        assertThrows(WorkbenchPreferenceInvalidException.class, () -> normalizer.normalize(payload, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"id,unknown", "size,XL", "hidden,text", "x,-1", "x,9", "y,47", "x,2147483647",
            "x,1.5", "w,text", "h,null"})
    void rejectsInvalidCardValues(String setting) throws Exception {
        String[] parts = setting.split(",");
        ObjectNode card = (ObjectNode) json.readTree(CARD);
        JsonNode value;
        try {
            value = json.readTree(parts[1]);
        } catch (Exception ignored) {
            value = json.getNodeFactory().textNode(parts[1]);
        }
        card.set(parts[0], value);
        JsonNode payload = json.createObjectNode().set("cards", json.createArrayNode().add(card));
        assertThrows(WorkbenchPreferenceInvalidException.class, () -> normalizer.normalize(payload, 1));
    }

    @Test
    void rejectsDuplicatesExcessCardsAndDockLimits() throws Exception {
        var cards = json.createArrayNode().add(json.readTree(CARD)).add(json.readTree(CARD));
        assertThrows(WorkbenchPreferenceInvalidException.class,
                () -> normalizer.normalize(json.createObjectNode().set("cards", cards), 1));
        for (int i = 0; i < 5; i++) {
            cards.add(json.readTree(CARD));
        }
        assertThrows(WorkbenchPreferenceInvalidException.class,
                () -> normalizer.normalize(json.createObjectNode().set("cards", cards), 1));
        ObjectNode payload = json.createObjectNode();
        var order = payload.putObject("dock").putArray("order");
        order.add("a".repeat(65));
        assertThrows(WorkbenchPreferenceInvalidException.class, () -> normalizer.normalize(payload, 1));
        order.removeAll();
        for (int i = 0; i < 101; i++) {
            order.add("");
        }
        assertThrows(WorkbenchPreferenceInvalidException.class, () -> normalizer.normalize(payload, 1));
    }

    @Test
    void enforcesUtf8BytesAfterUnknownFieldRemoval() {
        ObjectNode payload = json.createObjectNode();
        payload.put("ignored", "x".repeat(20000));
        assertDoesNotThrow(() -> normalizer.normalize(payload, 1));
        var order = payload.putObject("dock").putArray("order");
        for (int i = 0; i < 100; i++) {
            order.add("中".repeat(62) + i);
        }
        assertTrue(payload.toString().getBytes(StandardCharsets.UTF_8).length > 16384);
        assertThrows(WorkbenchPreferenceInvalidException.class, () -> normalizer.normalize(payload, 1));
    }

    @Test
    void upgradeV1ReturnsIndependentTreeAndRejectsUndefinedVersions() {
        ObjectNode payload = json.createObjectNode();
        assertNotSame(payload, normalizer.upgrade(payload, 1));
        assertEquals(payload, normalizer.upgrade(payload, 1));
        for (int schema : new int[]{-1, 0, 2}) {
            assertThrows(WorkbenchPreferenceInvalidException.class, () -> normalizer.upgrade(payload, schema));
        }
    }

    @Test
    void acceptsExactly16384BytesAndRejectsOneMoreByte() {
        ObjectNode payload = json.createObjectNode();
        var keys = payload.putObject("dock").putArray("order");
        for (int index = 0; index < 100; index++) {
            keys.add("k" + index);
        }
        // 在合法的 100 个短键内构造精确字节边界，不依赖未知字段填充。
        int current = normalizer.normalize(payload, 1).toString().getBytes(StandardCharsets.UTF_8).length;
        for (int index = 0; current < 16384 && index < keys.size(); index++) {
            String key = keys.get(index).textValue();
            while (key.length() < 63 && current < 16384) {
                String character = current <= 16381 ? "中" : "a";
                key += character;
                current += character.getBytes(StandardCharsets.UTF_8).length;
            }
            keys.set(index, json.getNodeFactory().textNode(key));
        }
        assertEquals(16384, normalizer.normalize(payload, 1).toString().getBytes(StandardCharsets.UTF_8).length);
        keys.set(0, json.getNodeFactory().textNode(keys.get(0).textValue() + "a"));
        assertThrows(WorkbenchPreferenceInvalidException.class, () -> normalizer.normalize(payload, 1));
    }

    @Test
    void storedCardsDiscardInvalidAndUnknownEntriesWithoutLosingValidCards() throws Exception {
        ObjectNode validCard = (ObjectNode) json.readTree(CARD);
        var cards = json.createArrayNode().addNull().add(1)
                .add(validCard.deepCopy().put("id", "removed"))
                .add(validCard.deepCopy().put("x", -1))
                .add(validCard.deepCopy().put("hidden", "false"))
                .add(validCard).add(validCard.deepCopy());
        ObjectNode source = json.createObjectNode().set("cards", cards);
        source.put("unknown", true);
        JsonNode before = source.deepCopy();
        var result = normalizer.normalizeStored(source, 1);
        assertEquals(1, result.get("cards").size());
        assertEquals("todo", result.at("/cards/0/id").textValue());
        assertEquals(4, result.at("/cards/0/w").intValue());
        assertFalse(result.has("unknown"));
        assertFalse(result.at("/cards/0").has("future"));
        assertEquals(before, source);
        assertEquals(result, normalizer.normalize(result, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "1", "\"text\""})
    void storedNonObjectPayloadReturnsNull(String source) throws Exception {
        assertNull(normalizer.normalizeStored(json.readTree(source), 1));
    }

    @Test
    void storedUnsupportedSchemaOrMissingPayloadReturnsNull() {
        for (int version : new int[]{-1, 0, 2}) {
            assertNull(normalizer.normalizeStored(json.createObjectNode(), version));
        }
        assertNull(normalizer.normalizeStored(null, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "1", "{\"collapsed\":1,\"order\":null}"})
    void storedInvalidDockPartsFallBackToDefaults(String dock) throws Exception {
        var result = normalizer.normalizeStored(json.readTree("{\"cards\":false,\"dock\":" + dock + "}"), 1);
        assertEquals(json.readTree("{\"cards\":[],\"dock\":{\"collapsed\":false,\"order\":[]}}"), result);
    }

    @Test
    void storedDockPreservesValidPartsAndDropsInvalidKeys() throws Exception {
        ObjectNode source = json.createObjectNode();
        var dock = source.putObject("dock").put("collapsed", true);
        var keys = dock.putArray("order").add(1).addNull().add(" x ").add("x").add("")
                .add("a".repeat(65)).add("nul-\0").add("high-\ud800").add("low-\udc00");
        assertEquals(json.readTree("{\"collapsed\":true,\"order\":[\"x\"]}"),
                normalizer.normalizeStored(source, 1).get("dock"));
        for (int index = 0; index < 110; index++) {
            keys.add("key-" + index);
        }
        assertEquals(100, normalizer.normalizeStored(source, 1).at("/dock/order").size());
    }

    @Test
    void storedOversizedNormalizedDocumentReturnsNullInsteadOf400() {
        ObjectNode source = json.createObjectNode();
        var order = source.putObject("dock").putArray("order");
        for (int index = 0; index < 100; index++) {
            order.add("中".repeat(62) + index);
        }
        assertNull(normalizer.normalizeStored(source, 1));
    }

    @Test
    void rejectsJsonbUnsupportedTextButAcceptsEmoji() {
        ObjectNode payload = json.createObjectNode();
        var keys = payload.putObject("dock").putArray("order");
        keys.add("emoji-😀");
        assertDoesNotThrow(() -> normalizer.normalize(payload, 1));
        for (String invalid : new String[]{"nul-\0", "high-\ud800", "low-\udc00"}) {
            keys.removeAll().add(invalid);
            assertThrows(WorkbenchPreferenceInvalidException.class, () -> normalizer.normalize(payload, 1));
        }
    }
}
