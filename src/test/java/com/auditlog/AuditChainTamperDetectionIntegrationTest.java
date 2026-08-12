package com.auditlog;

import com.auditlog.application.port.HashGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the threat boundary outside the application: direct changes to the
 * persisted audit data must be reported by the public verification endpoint.
 */
@SpringBootTest(properties = "audit.payload.base64-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
@AutoConfigureMockMvc
@WithMockUser(roles = "AUDIT_SERVICE")
class AuditChainTamperDetectionIntegrationTest {

    private static final String SENSITIVE_PAYLOAD_VALUE = "never-return-this-sensitive-plaintext";
    private static final String TAMPERED_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HashGenerator hashGenerator;

    private UUID firstEventId;
    private UUID secondEventId;

    @BeforeEach
    void createCleanTwoEventChain() throws Exception {
        resetDatabase();
        firstEventId = appendEvent("first-event");
        secondEventId = appendEvent("second-event");
    }

    @AfterEach
    void leaveSharedH2DatabaseEmpty() {
        resetDatabase();
    }

    @Test
    void reportsOrderViolationForSequenceChangedToZero() throws Exception {
        jdbcTemplate.update("UPDATE audit_event SET chain_sequence = 0 WHERE event_id = ?", secondEventId);

        assertViolation("ORDER_VIOLATION", secondEventId, 0);
    }

    @Test
    void reportsPredecessorHashMismatchForChangedPreviousHash() throws Exception {
        jdbcTemplate.update("UPDATE audit_event SET previous_hash = ? WHERE event_id = ?", TAMPERED_HASH, secondEventId);

        assertViolation("PREDECESSOR_HASH_MISMATCH", secondEventId, 2);
    }

    @Test
    void reportsPayloadMissingForDeletedEncryptedPayload() throws Exception {
        jdbcTemplate.update("DELETE FROM audit_event_payload WHERE event_id = ?", secondEventId);

        assertViolation("PAYLOAD_MISSING", secondEventId, 2);
    }

    @Test
    void reportsPayloadCiphertextMismatchForChangedCiphertext() throws Exception {
        byte[] changedCiphertext = tamperedCiphertext();
        jdbcTemplate.update("UPDATE audit_event_payload SET ciphertext = ? WHERE event_id = ?", changedCiphertext, secondEventId);

        assertViolation("PAYLOAD_CIPHERTEXT_MISMATCH", secondEventId, 2);
    }

    @Test
    void reportsPayloadCommitmentMismatchForChangedCommitment() throws Exception {
        jdbcTemplate.update("UPDATE audit_event SET payload_commitment = ? WHERE event_id = ?", TAMPERED_HASH, secondEventId);

        assertViolation("PAYLOAD_COMMITMENT_MISMATCH", secondEventId, 2);
    }

    @Test
    void reportsPayloadDecryptionFailureWhenCiphertextHashWasAlsoReplaced() throws Exception {
        byte[] changedCiphertext = tamperedCiphertext();
        jdbcTemplate.update(
                "UPDATE audit_event_payload SET ciphertext = ? WHERE event_id = ?",
                changedCiphertext,
                secondEventId);
        jdbcTemplate.update(
                "UPDATE audit_event SET payload_ciphertext_hash = ? WHERE event_id = ?",
                hashGenerator.hash(changedCiphertext),
                secondEventId);

        assertViolation("PAYLOAD_DECRYPTION_FAILURE", secondEventId, 2);
    }

    @Test
    void reportsContentHashMismatchForChangedProtectedField() throws Exception {
        jdbcTemplate.update("UPDATE audit_event SET actor_id = ? WHERE event_id = ?", "tampered-actor", secondEventId);

        assertViolation("CONTENT_HASH_MISMATCH", secondEventId, 2);
    }

    @Test
    void reportsChainHeadMissingWhenCoordinatorRowIsDeleted() throws Exception {
        jdbcTemplate.update("DELETE FROM chain_head WHERE chain_id = 1");

        assertViolation("CHAIN_HEAD_MISSING", null, null);
    }

    @Test
    void reportsChainHeadMismatchForChangedHeadHash() throws Exception {
        jdbcTemplate.update("UPDATE chain_head SET head_hash = ? WHERE chain_id = 1", TAMPERED_HASH);

        assertViolation("CHAIN_HEAD_MISMATCH", secondEventId, 2);
    }

    private UUID appendEvent(String eventType) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/audit/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "%s",
                                  "actorId": "tamper-test-actor",
                                  "resourceType": "ACCOUNT",
                                  "resourceId": "tamper-test-account",
                                  "payload": {"secret": "%s"}
                                }
                                """.formatted(eventType, SENSITIVE_PAYLOAD_VALUE)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return UUID.fromString(response.at("/data/eventId").asText());
    }

    private void assertViolation(String violationType, UUID expectedEventId, Integer expectedSequence) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/audit/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intact").value(false))
                .andExpect(jsonPath("$.data.violation.type").value(violationType))
                .andReturn();

        JsonNode violation = objectMapper.readTree(result.getResponse().getContentAsByteArray()).at("/data/violation");
        if (expectedEventId == null) {
            assertThat(violation.get("eventId").isNull()).isTrue();
        } else {
            assertThat(violation.path("eventId").asText()).isEqualTo(expectedEventId.toString());
        }
        if (expectedSequence == null) {
            assertThat(violation.get("chainSequence").isNull()).isTrue();
        } else {
            assertThat(violation.path("chainSequence").asInt()).isEqualTo(expectedSequence);
        }
        assertThat(result.getResponse().getContentAsString()).doesNotContain(SENSITIVE_PAYLOAD_VALUE);
    }

    private byte[] tamperedCiphertext() {
        byte[] ciphertext = jdbcTemplate.queryForObject(
                "SELECT ciphertext FROM audit_event_payload WHERE event_id = ?",
                byte[].class,
                secondEventId);
        ciphertext[ciphertext.length - 1] ^= 1;
        return ciphertext;
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
