package com.auditlog.support.utility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Produces a deterministic JSON representation for cryptographic hashing.
 * Object keys are sorted recursively, array order is preserved, and decimal values
 * are normalized to avoid distinct hashes for semantically equivalent numeric input.
 */
@Component
public class CanonicalJsonSerializer {

    private final ObjectMapper objectMapper;

    public CanonicalJsonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] serialize(JsonNode value) {
        try {
            return objectMapper.writeValueAsBytes(canonicalize(value));
        } catch (JsonProcessingException exception) {
            throw new CanonicalizationException("Unable to serialize canonical JSON", exception);
        }
    }

    private JsonNode canonicalize(JsonNode value) {
        if (value.isObject()) {
            return canonicalizeObject(value);
        }
        if (value.isArray()) {
            return canonicalizeArray(value);
        }
        if (value.isNumber()) {
            return canonicalizeNumber(value.decimalValue());
        }
        return value;
    }

    private ObjectNode canonicalizeObject(JsonNode value) {
        ObjectNode canonicalObject = JsonNodeFactory.instance.objectNode();
        Map<String, JsonNode> orderedFields = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
        fields.forEachRemaining(field -> orderedFields.put(field.getKey(), field.getValue()));
        orderedFields.forEach((fieldName, fieldValue) -> canonicalObject.set(fieldName, canonicalize(fieldValue)));
        return canonicalObject;
    }

    private ArrayNode canonicalizeArray(JsonNode value) {
        ArrayNode canonicalArray = JsonNodeFactory.instance.arrayNode();
        value.forEach(item -> canonicalArray.add(canonicalize(item)));
        return canonicalArray;
    }

    private DecimalNode canonicalizeNumber(BigDecimal value) {
        BigDecimal normalized = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        return DecimalNode.valueOf(normalized);
    }

    public static class CanonicalizationException extends RuntimeException {

        public CanonicalizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
