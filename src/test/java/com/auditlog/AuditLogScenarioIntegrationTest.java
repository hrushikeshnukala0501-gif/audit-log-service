package com.auditlog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "audit.payload.base64-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "audit.retention.archive-after=P365D"})
@AutoConfigureMockMvc
class AuditLogScenarioIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void scenarioAtoC_mainFlow_isTamperEvidentAndQueryable() throws Exception {
        String first = "{\"eventType\":\"CLIENT_ACCOUNT_ACCESSED\",\"actorId\":\"advisor-1\",\"resourceType\":\"ACCOUNT\",\"resourceId\":\"account-1\",\"payload\":{\"accountNumber\":\"123\",\"nested\":{\"ssn\":\"999\"}}}";
        String second = "{\"eventType\":\"RECORD_UPDATED\",\"actorId\":\"advisor-1\",\"resourceType\":\"ACCOUNT\",\"resourceId\":\"account-1\",\"payload\":{\"field\":\"email\"}}";

        mockMvc.perform(post("/api/v1/audit/events").contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.chainSequence").value(1))
                .andExpect(jsonPath("$.data.previousHash").value("6f631d53cdc1f26efef6d78054e78948c5681f71267c3f9370cf7e5a7a134b39"));
        String eventId = mockMvc.perform(post("/api/v1/audit/events").contentType(MediaType.APPLICATION_JSON).content(second))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.chainSequence").value(2))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\\\"eventId\\\":\\\"([^\\\"]+).*", "$1");

        mockMvc.perform(get("/api/v1/audit/events").param("actorId", "advisor-1").param("pageSize", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.events.length()").value(1)).andExpect(jsonPath("$.data.nextCursor").exists());
        mockMvc.perform(get("/api/v1/audit/verify")).andExpect(status().isOk()).andExpect(jsonPath("$.data.intact").value(true));
        mockMvc.perform(get("/api/v1/audit/export").param("actorId", "advisor-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.events.length()").value(2)).andExpect(jsonPath("$.data.bundleHash").exists());
        mockMvc.perform(get("/api/v1/compliance/client-account-access").param("accountId", "account-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records.length()").value(2));
        mockMvc.perform(post("/api/v1/audit/events/" + eventId + "/redactions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonPointer\":\"/field\",\"reason\":\"test\",\"policyVersion\":\"v1\",\"authorizedBy\":\"privacy\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/audit/events").param("eventType", "RECORD_UPDATED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.events[0].payload.field").value("[REDACTED]"));
        mockMvc.perform(get("/api/v1/audit/verify")).andExpect(status().isOk()).andExpect(jsonPath("$.data.intact").value(true));
    }

    @Test
    void invalidRequests_areRejected() throws Exception {
        mockMvc.perform(post("/api/v1/audit/events").contentType(MediaType.APPLICATION_JSON).content("{\"eventType\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/audit/export")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/compliance/client-account-access").param("accountId", "account-1").param("from", "2026-12-01T00:00:00Z").param("to", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }
}
