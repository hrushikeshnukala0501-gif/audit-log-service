package com.auditlog;

import com.auditlog.application.command.AppendAuditEventCommand;
import com.auditlog.application.result.AppendedAuditEvent;
import com.auditlog.application.result.ChainVerificationResult;
import com.auditlog.application.service.AuditChainVerificationService;
import com.auditlog.application.service.AuditEventAppendService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that concurrent append attempts serialize through the pessimistically
 * locked global chain head rather than creating divergent predecessor links.
 */
@SpringBootTest(properties = "audit.payload.base64-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
class AuditEventConcurrencyIntegrationTest {

    private static final int CONCURRENT_APPENDS = 8;
    private static final long TEST_TIMEOUT_SECONDS = 10;

    @Autowired
    private AuditEventAppendService appendService;

    @Autowired
    private AuditChainVerificationService verificationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void startWithAnEmptyAuditChain() {
        resetDatabase();
    }

    @AfterEach
    void leaveSharedH2DatabaseEmpty() {
        resetDatabase();
    }

    @Test
    void serializesConcurrentAppendsIntoOneIntactPredecessorChain() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_APPENDS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_APPENDS);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<AppendedAuditEvent>> futures = java.util.stream.IntStream.range(0, CONCURRENT_APPENDS)
                    .mapToObj(index -> executor.submit(() -> appendAfterConcurrentStart(index, ready, start)))
                    .toList();

            assertThat(ready.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<AppendedAuditEvent> appendedEvents = futures.stream()
                    .map(this::awaitResult)
                    .sorted(Comparator.comparingLong(AppendedAuditEvent::chainSequence))
                    .toList();

            assertThat(appendedEvents).extracting(AppendedAuditEvent::chainSequence)
                    .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
            for (int index = 1; index < appendedEvents.size(); index++) {
                assertThat(appendedEvents.get(index).previousHash())
                        .isEqualTo(appendedEvents.get(index - 1).contentHash());
            }

            ChainVerificationResult verification = verificationService.verify();
            assertThat(verification.intact()).isTrue();
            assertThat(verification.verifiedThroughSequence()).isEqualTo(CONCURRENT_APPENDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    private AppendedAuditEvent appendAfterConcurrentStart(int index, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to start concurrent append test");
        }
        return appendService.append(new AppendAuditEventCommand(
                "CONCURRENT_APPEND",
                "concurrent-actor-" + index,
                "ACCOUNT",
                "concurrent-account-" + index,
                objectMapper.createObjectNode().put("request", index)));
    }

    private AppendedAuditEvent awaitResult(Future<AppendedAuditEvent> future) {
        try {
            return future.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Concurrent append did not complete successfully", exception);
        }
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
