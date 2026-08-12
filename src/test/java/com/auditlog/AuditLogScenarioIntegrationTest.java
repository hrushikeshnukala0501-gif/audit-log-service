package com.auditlog;

import com.auditlog.config.AuditHashProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "audit.payload.base64-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "audit.retention.archive-after=P365D"})
@AutoConfigureMockMvc
@WithMockUser(roles = "AUDIT_SERVICE")
class AuditLogScenarioIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditHashProperties hashProperties;

    @BeforeEach
    void startWithAnEmptyAuditChain() {
        resetDatabase();
    }

    @AfterEach
    void leaveSharedH2DatabaseEmpty() {
        resetDatabase();
    }

    @Test
    void appendsFirstEventUsingConfiguredGenesisHash() throws Exception {
        JsonNode event = appendEvent("RECORD_CREATED", "advisor-1", "account-1", payload("field", "address"));

        assertThat(event.path("chainSequence").asLong()).isEqualTo(1);
        assertThat(event.path("previousHash").asText()).isEqualTo(hashProperties.genesisHash());
        assertThat(event.path("contentHash").asText()).hasSize(64);
    }

    @Test
    void linksLaterEventToPreviousContentHash() throws Exception {
        JsonNode first = appendEvent("RECORD_CREATED", "advisor-1", "account-1", payload("field", "address"));
        JsonNode second = appendEvent("RECORD_UPDATED", "advisor-1", "account-1", payload("field", "email"));

        assertThat(second.path("chainSequence").asLong()).isEqualTo(2);
        assertThat(second.path("previousHash").asText()).isEqualTo(first.path("contentHash").asText());
    }

    @Test
    void queriesEventsUsingActorFilterAndCursorPagination() throws Exception {
        appendEvent("RECORD_CREATED", "advisor-1", "account-1", payload("field", "address"));
        appendEvent("RECORD_UPDATED", "advisor-1", "account-1", payload("field", "email"));
        appendEvent("RECORD_UPDATED", "advisor-2", "account-2", payload("field", "phone"));

        MvcResult firstPageResult = mockMvc.perform(get("/api/v1/audit/events")
                        .param("actorId", "advisor-1")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events.length()").value(1))
                .andExpect(jsonPath("$.data.events[0].actorId").value("advisor-1"))
                .andReturn();
        String cursor = response(firstPageResult).at("/data/nextCursor").asText();

        mockMvc.perform(get("/api/v1/audit/events")
                        .param("actorId", "advisor-1")
                        .param("pageSize", "1")
                        .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events.length()").value(1))
                .andExpect(jsonPath("$.data.events[0].chainSequence").value(2))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    void verifiesAnIntactChain() throws Exception {
        appendEvent("RECORD_CREATED", "advisor-1", "account-1", payload("field", "address"));
        appendEvent("RECORD_UPDATED", "advisor-1", "account-1", payload("field", "email"));

        mockMvc.perform(get("/api/v1/audit/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intact").value(true))
                .andExpect(jsonPath("$.data.completeChainVerification").value(true))
                .andExpect(jsonPath("$.data.verifiedThroughSequence").value(2));
    }

    @Test
    void verifiesBoundedRangeWithPredecessorContinuity() throws Exception {
        appendEvent("RECORD_CREATED", "advisor-1", "account-1", payload("field", "address"));
        appendEvent("RECORD_UPDATED", "advisor-1", "account-1", payload("field", "email"));
        appendEvent("RECORD_UPDATED", "advisor-1", "account-1", payload("field", "phone"));

        mockMvc.perform(get("/api/v1/audit/verify")
                        .param("fromSequence", "2")
                        .param("toSequence", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intact").value(true))
                .andExpect(jsonPath("$.data.completeChainVerification").value(false))
                .andExpect(jsonPath("$.data.verifiedFromSequence").value(2))
                .andExpect(jsonPath("$.data.verifiedThroughSequence").value(3));
    }

    @Test
    void verifiesBoundedRangeBeginningAtGenesis() throws Exception {
        appendEvent("RECORD_CREATED", "advisor-1", "account-1", payload("field", "address"));
        appendEvent("RECORD_UPDATED", "advisor-1", "account-1", payload("field", "email"));

        mockMvc.perform(get("/api/v1/audit/verify")
                        .param("fromSequence", "1")
                        .param("toSequence", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intact").value(true))
                .andExpect(jsonPath("$.data.completeChainVerification").value(false))
                .andExpect(jsonPath("$.data.verifiedFromSequence").value(1))
                .andExpect(jsonPath("$.data.verifiedThroughSequence").value(1));
    }

    @Test
    void reportsBrokenPredecessorAtStartOfBoundedRange() throws Exception {
        appendEvent("RECORD_CREATED", "advisor-1", "account-1", payload("field", "address"));
        JsonNode second = appendEvent("RECORD_UPDATED", "advisor-1", "account-1", payload("field", "email"));
        jdbcTemplate.update(
                "UPDATE audit_event SET previous_hash = ? WHERE event_id = ?",
                "0000000000000000000000000000000000000000000000000000000000000000",
                java.util.UUID.fromString(second.path("eventId").asText()));

        mockMvc.perform(get("/api/v1/audit/verify")
                        .param("fromSequence", "2")
                        .param("toSequence", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intact").value(false))
                .andExpect(jsonPath("$.data.violation.type").value("PREDECESSOR_HASH_MISMATCH"))
                .andExpect(jsonPath("$.data.violation.chainSequence").value(2));
    }

    @Test
    void rejectsInvalidVerificationRange() throws Exception {
        mockMvc.perform(get("/api/v1/audit/verify")
                        .param("fromSequence", "3")
                        .param("toSequence", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void exportsEventsForActor() throws Exception {
        appendEvent("RECORD_CREATED", "advisor-1", "account-1", payload("field", "address"));
        appendEvent("RECORD_UPDATED", "advisor-1", "account-1", payload("field", "email"));

        mockMvc.perform(get("/api/v1/audit/export").param("actorId", "advisor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectorType").value("actorId"))
                .andExpect(jsonPath("$.data.selectorValue").value("advisor-1"))
                .andExpect(jsonPath("$.data.events.length()").value(2))
                .andExpect(jsonPath("$.data.bundleHash").isNotEmpty());
    }

    @Test
    void returnsAccountComplianceReportWithoutPayload() throws Exception {
        appendEvent("CLIENT_ACCOUNT_ACCESSED", "advisor-1", "account-1", payload("accountNumber", "123"));

        mockMvc.perform(get("/api/v1/compliance/client-account-access").param("accountId", "account-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value("account-1"))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].eventType").value("CLIENT_ACCOUNT_ACCESSED"))
                .andExpect(jsonPath("$.data.records[0].payload").doesNotExist())
                .andExpect(jsonPath("$.data.reportHash").isNotEmpty());
    }

    @Test
    void redactsPayloadProjectionWithoutBreakingChain() throws Exception {
        JsonNode event = appendEvent("RECORD_UPDATED", "advisor-1", "account-1", payload("email", "client@example.test"));
        String eventId = event.path("eventId").asText();

        mockMvc.perform(post("/api/v1/audit/events/{eventId}/redactions", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jsonPointer": "/email",
                                  "reason": "test redaction",
                                  "policyVersion": "v1",
                                  "authorizedBy": "privacy-officer"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/audit/events").param("eventType", "RECORD_UPDATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[0].payload.email").value("[REDACTED]"));
        mockMvc.perform(get("/api/v1/audit/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intact").value(true));
    }

    @Test
    void rejectsInvalidRequests() throws Exception {
        mockMvc.perform(post("/api/v1/audit/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/audit/export"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/compliance/client-account-access")
                        .param("accountId", "account-1")
                        .param("from", "2026-12-01T00:00:00Z")
                        .param("to", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode appendEvent(String eventType, String actorId, String resourceId, ObjectNode payload) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("eventType", eventType);
        request.put("actorId", actorId);
        request.put("resourceType", "ACCOUNT");
        request.put("resourceId", resourceId);
        request.set("payload", payload);

        MvcResult result = mockMvc.perform(post("/api/v1/audit/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return response(result).at("/data");
    }

    private ObjectNode payload(String field, String value) {
        return objectMapper.createObjectNode().put(field, value);
    }

    private JsonNode response(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void resetDatabase() {
        jdbcTemplate.update("DELETE FROM payload_redaction");
        jdbcTemplate.update("DELETE FROM archive_manifest");
        jdbcTemplate.update("DELETE FROM audit_write_request");
        jdbcTemplate.update("DELETE FROM chain_checkpoint");
        jdbcTemplate.update("DELETE FROM audit_event_payload");
        jdbcTemplate.update("DELETE FROM chain_head");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.execute("ALTER SEQUENCE audit_event_sequence RESTART WITH 1");
        jdbcTemplate.update("INSERT INTO chain_head (chain_id, updated_at) VALUES (1, CURRENT_TIMESTAMP)");
    }
}
