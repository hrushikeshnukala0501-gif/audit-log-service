package com.auditlog.support.utility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalJsonSerializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CanonicalJsonSerializer serializer = new CanonicalJsonSerializer(objectMapper);

    @Test
    void sortsObjectKeysRecursivelyWhilePreservingArrayOrder() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"z":{"b":2,"a":1},"items":[{"d":4,"c":3},2,1],"a":0}
                """);

        assertThat(canonicalJson(payload)).isEqualTo(
                "{\"a\":0,\"items\":[{\"c\":3,\"d\":4},2,1],\"z\":{\"a\":1,\"b\":2}}");
    }

    @Test
    void normalizesEquivalentDecimalRepresentations() throws Exception {
        JsonNode first = objectMapper.readTree("{" + "\"amount\":1.2300,\"zero\":-0.00}");
        JsonNode second = objectMapper.readTree("{" + "\"amount\":1.23,\"zero\":0}");

        assertThat(serializer.serialize(first)).isEqualTo(serializer.serialize(second));
        assertThat(canonicalJson(first)).isEqualTo("{\"amount\":1.23,\"zero\":0}");
    }

    @Test
    void preservesUnicodeContentAsUtf8Json() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"message\":\"Åudit 😀\"}");

        assertThat(canonicalJson(payload)).isEqualTo("{\"message\":\"Åudit \\uD83D\\uDE00\"}");
    }

    @Test
    void serializesEmptyObjectsInsideNestedArraysDeterministically() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"values\":[{}, {\"b\":2,\"a\":1}, []]}");

        assertThat(canonicalJson(payload)).isEqualTo("{\"values\":[{},{\"a\":1,\"b\":2},[]]}");
    }

    private String canonicalJson(JsonNode value) {
        return new String(serializer.serialize(value), StandardCharsets.UTF_8);
    }
}
