package co.edu.univalle.fleetops.vehicles.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import co.edu.univalle.fleetops.vehicles.infrastructure.support.LocalStackSupport;
import co.edu.univalle.fleetops.vehicles.infrastructure.support.MessagingIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/** De SQS al caso de uso: mensaje nuevo, duplicado, tipo desconocido, error y DLQ. */
class SqsToUseCaseIntegrationTest extends MessagingIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(40);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MeterRegistry meters;

    private static String envelope(String eventId, String eventType, String correlationId) {
        return "{\"event_id\":\"" + eventId + "\",\"event_type\":\"" + eventType + "\","
                + "\"occurred_at\":\"2026-10-27T09:30:02-05:00\",\"source\":\"incident\","
                + "\"correlation_id\":\"" + correlationId + "\",\"data\":{\"vehicle_id\":\"v-1\"}}";
    }

    private static void send(String body) {
        LocalStackSupport.sqs().sendMessage(request -> request.queueUrl(LocalStackSupport.queueUrl())
                .messageBody(body));
    }

    private double consumed() {
        return meters.get("vehicles.messages.consumed").counter().count();
    }

    private double consumeErrors() {
        return meters.get("vehicles.messaging.errors").tag("operation", "consume").counter().count();
    }

    private int messagesIn(String queueUrl) {
        var attributes = LocalStackSupport.sqs().getQueueAttributes(request -> request.queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)).attributes();
        return Integer.parseInt(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                + Integer.parseInt(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
    }

    @Test
    void listener_newMessage_runsTheUseCaseRecordsItAndDeletesTheMessage() {
        double before = consumed();
        String correlation = UUID.randomUUID().toString();

        send(envelope("0b9a7c55-2f1e-4d3a-8b6c-5e4f3a2b1c0d", OK_EVENT, correlation));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(consumed()).isEqualTo(before + 1));
        assertThat(HANDLED).singleElement().satisfies(event -> {
            assertThat(event.eventId()).isEqualTo("0b9a7c55-2f1e-4d3a-8b6c-5e4f3a2b1c0d");
            assertThat(event.data()).containsEntry("vehicle_id", "v-1");
        });
        assertThat(jdbc.queryForList("SELECT event_id FROM processed_events", String.class))
                .containsExactly("0b9a7c55-2f1e-4d3a-8b6c-5e4f3a2b1c0d");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payload->>'correlation_id' FROM outbox_events", String.class))
                .as("the event left by the handler keeps the correlation id of the message")
                .isEqualTo(correlation);
        assertThat(messagesIn(LocalStackSupport.queueUrl())).isZero();
    }

    @Test
    void listener_idNotUuid_isAcceptedAsText() {
        double before = consumed();

        send(envelope("hash-of-document-and-EXPIRED", OK_EVENT, "corr"));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(consumed()).isEqualTo(before + 1));
        assertThat(jdbc.queryForList("SELECT event_id FROM processed_events", String.class))
                .containsExactly("hash-of-document-and-EXPIRED");
    }

    @Test
    void listener_duplicatedMessage_isDeletedWithoutRunningTheUseCaseAgain() {
        double before = consumed();
        String body = envelope("7d2e9f10-4b6a-4c8e-9a1b-2c3d4e5f6a7b", OK_EVENT, "corr");

        send(body);
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(consumed()).isEqualTo(before + 1));
        send(body);
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(consumed()).isEqualTo(before + 2));

        assertThat(HANDLED).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isEqualTo(1);
        assertThat(messagesIn(LocalStackSupport.queueUrl())).isZero();
    }

    @Test
    void listener_unknownEventType_isLoggedAndDeletedWithoutError() {
        double consumedBefore = consumed();
        double errorsBefore = consumeErrors();

        send(envelope("5c8e1f2a-9b3d-4e7f-8a6b-1c2d3e4f5a6b", "RutaFinalizadaEvent", "corr"));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(consumed()).isEqualTo(consumedBefore + 1));
        assertThat(consumeErrors()).isEqualTo(errorsBefore);
        assertThat(HANDLED).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events", Integer.class)).isZero();
        assertThat(messagesIn(LocalStackSupport.queueUrl())).isZero();
    }

    @Test
    void listener_useCaseFails_leavesNoPartialChangesAndTheMessageEndsInTheDlqAfterThreeFailures() {
        double errorsBefore = consumeErrors();

        send(envelope("9e4b2c7d-1a3f-4d5e-8b9c-0a1b2c3d4e5f", FAILING_EVENT, "corr"));

        await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(500)).untilAsserted(
                () -> assertThat(messagesIn(LocalStackSupport.dlqUrl())).isEqualTo(1));
        assertThat(HANDLED).hasSize(3);
        assertThat(consumeErrors()).isGreaterThanOrEqualTo(errorsBefore + 3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events", Integer.class))
                .as("the rollback also removes the processed_events row").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class))
                .as("the rollback also removes the event the handler left in the outbox").isZero();
        assertThat(messagesIn(LocalStackSupport.queueUrl())).isZero();
    }

    @Test
    void listener_bodyWithoutEventId_isNotDeletedAndEndsInTheDlq() {
        send("{\"event_type\":\"" + OK_EVENT + "\",\"data\":{}}");

        await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(500)).untilAsserted(
                () -> assertThat(messagesIn(LocalStackSupport.dlqUrl())).isEqualTo(1));
        assertThat(HANDLED).isEmpty();
    }
}
