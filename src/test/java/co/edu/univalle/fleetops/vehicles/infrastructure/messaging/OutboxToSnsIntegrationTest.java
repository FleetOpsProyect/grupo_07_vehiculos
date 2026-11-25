package co.edu.univalle.fleetops.vehicles.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import co.edu.univalle.fleetops.vehicles.application.port.out.EventOutbox;
import co.edu.univalle.fleetops.vehicles.application.port.out.TransactionPort;
import co.edu.univalle.fleetops.vehicles.infrastructure.support.LocalStackSupport;
import co.edu.univalle.fleetops.vehicles.infrastructure.support.MessagingIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.sqs.model.Message;

/** Del outbox a SNS de punta a punta, con PostgreSQL y LocalStack reales. */
class OutboxToSnsIntegrationTest extends MessagingIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(25);

    @Autowired
    private EventOutbox outbox;
    @Autowired
    private TransactionPort transaction;
    @Autowired
    private JdbcTemplate jdbc;

    private void record(String type, UUID vehicleId) {
        transaction.execute(() -> {
            outbox.record(type, vehicleId, Map.of("vehicle_id", vehicleId.toString()));
            return null;
        });
    }

    private Optional<Message> probeMessage() {
        List<Message> messages = LocalStackSupport.sqs().receiveMessage(request -> request
                .queueUrl(LocalStackSupport.probeUrl()).messageAttributeNames("All").waitTimeSeconds(1)
                .maxNumberOfMessages(1)).messages();
        return messages.stream().findFirst();
    }

    @Test
    void publisher_pendingEvent_arrivesToTheTopicWithEventTypeAttributeAndIsMarkedPublished() throws Exception {
        UUID vehicleId = UUID.randomUUID();

        record("vehicle_created_event", vehicleId);

        Message message = await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(200))
                .until(this::probeMessage, Optional::isPresent).orElseThrow();
        assertThat(message.messageAttributes().get("event_type").stringValue()).isEqualTo("vehicle_created_event");
        JsonNode envelope = new ObjectMapper().readTree(message.body());
        assertThat(envelope.path("event_type").asText()).isEqualTo("vehicle_created_event");
        assertThat(envelope.path("source").asText()).isEqualTo("vehicle");
        assertThat(envelope.path("data").path("vehicle_id").asText()).isEqualTo(vehicleId.toString());
        await().atMost(TIMEOUT).untilAsserted(() -> {
            Map<String, Object> row = jdbc.queryForMap("SELECT status, published_at FROM outbox_events");
            assertThat(row.get("status")).isEqualTo("PUBLISHED");
            assertThat(row.get("published_at")).isNotNull();
        });
    }

    @Test
    void publisher_snsDown_keepsTheEventPendingCountsTheAttemptAndPublishesItWhenSnsReturns() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        LocalStackSupport.deleteTopic();
        try {
            record("vehicle_created_event", first);
            record("vehicle_updated_event", second);

            await().atMost(TIMEOUT).untilAsserted(() -> assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM outbox_events WHERE attempts >= 2 AND last_error IS NOT NULL",
                    Integer.class)).isEqualTo(2));
            assertThat(jdbc.queryForList("SELECT status FROM outbox_events", String.class))
                    .containsOnly("PENDING");
        } finally {
            LocalStackSupport.restoreTopic();
        }

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(jdbc.queryForList(
                "SELECT status FROM outbox_events", String.class)).containsOnly("PUBLISHED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE last_error IS NULL",
                Integer.class)).isEqualTo(2);
    }
}
