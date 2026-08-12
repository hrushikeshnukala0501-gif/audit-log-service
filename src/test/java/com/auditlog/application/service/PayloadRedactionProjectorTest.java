package com.auditlog.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadRedactionProjectorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PayloadRedactionProjector projector = new PayloadRedactionProjector();

    @Test
    void redactsNestedObjectFieldWithoutChangingSiblingFields() throws Exception {
        JsonNode result = project("{\"contact\":{\"email\":\"client@example.test\",\"phone\":\"123\"}}", "/contact/email");

        assertThat(result.at("/contact/email").asText()).isEqualTo("[REDACTED]");
        assertThat(result.at("/contact/phone").asText()).isEqualTo("123");
    }

    @Test
    void redactsArrayElementAtJsonPointerIndex() throws Exception {
        JsonNode result = project("{\"accounts\":[\"first\",\"second\"]}", "/accounts/1");

        assertThat(result.at("/accounts/0").asText()).isEqualTo("first");
        assertThat(result.at("/accounts/1").asText()).isEqualTo("[REDACTED]");
    }

    @Test
    void missingObjectPointerCreatesOnlyTheRedactionMarker() throws Exception {
        JsonNode result = project("{\"existing\":\"value\"}", "/missing");

        assertThat(result.at("/existing").asText()).isEqualTo("value");
        assertThat(result.at("/missing").asText()).isEqualTo("[REDACTED]");
    }

    @Test
    void redactsWholeNestedObjectAtPointer() throws Exception {
        JsonNode result = project("{\"identity\":{\"name\":\"Client\",\"taxId\":\"123\"},\"status\":\"active\"}", "/identity");

        assertThat(result.at("/identity").asText()).isEqualTo("[REDACTED]");
        assertThat(result.at("/status").asText()).isEqualTo("active");
    }

    private JsonNode project(String payload, String pointer) throws Exception {
        return projector.project(objectMapper.readTree(payload), List.of(pointer));
    }
}
