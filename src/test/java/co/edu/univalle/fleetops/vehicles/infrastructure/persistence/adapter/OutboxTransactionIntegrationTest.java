package co.edu.univalle.fleetops.vehicles.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.univalle.fleetops.vehicles.application.event.PendingEvent;
import co.edu.univalle.fleetops.vehicles.application.port.out.EventOutbox;
import co.edu.univalle.fleetops.vehicles.application.port.out.OutboxQueue;
import co.edu.univalle.fleetops.vehicles.application.port.out.ProcessedEventRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.TransactionPort;
import co.edu.univalle.fleetops.vehicles.infrastructure.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** El outbox y el registro de mensajes procesados frente a una base de datos real. */
class OutboxTransactionIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private EventOutbox outbox;
    @Autowired
    private OutboxQueue queue;
    @Autowired
    private ProcessedEventRepository processed;
    @Autowired
    private TransactionPort transaction;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private void record(String type, UUID vehicleId) {
        transaction.execute(() -> {
            outbox.record(type, vehicleId, Map.of("vehicle_id", vehicleId.toString()));
            return null;
        });
    }

    @Test
    void migration_createsBothTablesWithTheirIndexAndTextEventId() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_name IN "
                        + "('outbox_events', 'processed_events') ORDER BY table_name", String.class);
        String eventIdType = jdbc.queryForObject("SELECT data_type FROM information_schema.columns "
                + "WHERE table_name = 'processed_events' AND column_name = 'event_id'", String.class);
        String payloadType = jdbc.queryForObject("SELECT data_type FROM information_schema.columns "
                + "WHERE table_name = 'outbox_events' AND column_name = 'payload'", String.class);
        String indexDefinition = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_outbox_status_created'", String.class);

        assertThat(tables).containsExactly("outbox_events", "processed_events");
        assertThat(eventIdType).isEqualTo("character varying");
        assertThat(payloadType).isEqualTo("jsonb");
        assertThat(indexDefinition).contains("(status, created_at)");
    }

    @Test
    void record_committedTransaction_leavesAPendingRowWithTheFullEnvelope() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        MDC.put("correlation_id", "6f1c2b7e-3d4a-4b5c-9d6e-7f8a9b0c1d2e");

        record("vehicle_created_event", vehicleId);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM outbox_events");
        assertThat(row).containsEntry("status", "PENDING").containsEntry("attempts", 0)
                .containsEntry("event_type", "vehicle_created_event").containsEntry("aggregate_id", vehicleId);
        assertThat(row.get("last_error")).isNull();
        assertThat(row.get("published_at")).isNull();
        JsonNode payload = new ObjectMapper().readTree(jdbc.queryForObject(
                "SELECT CAST(payload AS text) FROM outbox_events", String.class));
        assertThat(payload.path("event_id").asText()).isEqualTo(row.get("event_id").toString());
        assertThat(payload.path("event_type").asText()).isEqualTo("vehicle_created_event");
        assertThat(payload.path("source").asText()).isEqualTo("vehicle");
        assertThat(payload.path("correlation_id").asText()).isEqualTo("6f1c2b7e-3d4a-4b5c-9d6e-7f8a9b0c1d2e");
        assertThat(payload.path("occurred_at").asText()).matches("\\d{4}-\\d{2}-\\d{2}T[\\d:.]+-05:00");
        assertThat(payload.path("data").path("vehicle_id").asText()).isEqualTo(vehicleId.toString());
    }

    @Test
    void record_withoutCorrelationInTheContext_generatesOne() throws Exception {
        record("vehicle_created_event", UUID.randomUUID());

        String correlation = jdbc.queryForObject("SELECT payload->>'correlation_id' FROM outbox_events",
                String.class);

        assertThat(UUID.fromString(correlation)).isNotNull();
    }

    @Test
    void record_rolledBackTransaction_leavesNoRow() {
        assertThatThrownBy(() -> transaction.execute(() -> {
            outbox.record("vehicle_created_event", UUID.randomUUID(), Map.of());
            throw new IllegalStateException("the change failed after recording the event");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isZero();
    }

    @Test
    void lockPending_returnsOnlyPendingEventsOldestFirstAndHonoursTheLimit() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        record("first_event", vehicleId);
        record("second_event", vehicleId);
        record("third_event", vehicleId);
        UUID published = jdbc.queryForObject("SELECT event_id FROM outbox_events WHERE event_type = 'first_event'",
                UUID.class);
        queue.markPublished(published, OffsetDateTime.now());

        List<PendingEvent> pending = transaction.execute(() -> queue.lockPending(1));

        assertThat(pending).extracting(PendingEvent::eventType).containsExactly("second_event");
        assertThat(pending.get(0).payload()).contains("\"event_type\": \"second_event\"");
    }

    @Test
    void lockPending_twoPublishersAtTheSameTime_doNotTakeTheSameEvent() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        record("first_event", vehicleId);
        record("second_event", vehicleId);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<List<PendingEvent>> holder = executor.submit(() -> transaction.execute(() -> {
                List<PendingEvent> mine = queue.lockPending(1);
                locked.countDown();
                await(release);
                return mine;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            List<PendingEvent> other = transaction.execute(() -> queue.lockPending(10));

            assertThat(other).extracting(PendingEvent::eventType).containsExactly("second_event");
            release.countDown();
            assertThat(holder.get(10, TimeUnit.SECONDS)).extracting(PendingEvent::eventType)
                    .containsExactly("first_event");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void markFailed_addsAnAttemptAndKeepsTheEventPending() {
        record("vehicle_created_event", UUID.randomUUID());
        UUID eventId = jdbc.queryForObject("SELECT event_id FROM outbox_events", UUID.class);

        queue.markFailed(eventId, "SdkClientException: connection refused");
        queue.markFailed(eventId, "SdkClientException: timeout");

        Map<String, Object> row = jdbc.queryForMap("SELECT status, attempts, last_error FROM outbox_events");
        assertThat(row).containsEntry("status", "PENDING").containsEntry("attempts", 2)
                .containsEntry("last_error", "SdkClientException: timeout");
    }

    @Test
    void markPublished_setsStatusAndDateAndClearsThePreviousError() {
        record("vehicle_created_event", UUID.randomUUID());
        UUID eventId = jdbc.queryForObject("SELECT event_id FROM outbox_events", UUID.class);
        queue.markFailed(eventId, "boom");
        OffsetDateTime when = OffsetDateTime.parse("2026-10-27T10:30:00-05:00");

        queue.markPublished(eventId, when);

        Map<String, Object> row = jdbc.queryForMap("SELECT status, published_at, last_error FROM outbox_events");
        assertThat(row).containsEntry("status", "PUBLISHED");
        assertThat(row.get("last_error")).isNull();
        assertThat(((java.sql.Timestamp) row.get("published_at")).toInstant()).isEqualTo(when.toInstant());
    }

    @Test
    void markProcessed_firstTimeTrueAndRepeatedFalse() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-27T10:30:00-05:00");

        boolean first = processed.markProcessed("hash-of-document-and-EXPIRED", "document_expired_event", now);
        boolean second = processed.markProcessed("hash-of-document-and-EXPIRED", "document_expired_event", now);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events", Integer.class)).isEqualTo(1);
    }

    @Test
    void markProcessed_rolledBackTransaction_forgetsTheEvent() {
        OffsetDateTime now = OffsetDateTime.now();
        assertThatThrownBy(() -> transaction.execute(() -> {
            processed.markProcessed("evt-1", "document_expired_event", now);
            throw new IllegalStateException("the use case failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(processed.markProcessed("evt-1", "document_expired_event", now)).isTrue();
    }
}
