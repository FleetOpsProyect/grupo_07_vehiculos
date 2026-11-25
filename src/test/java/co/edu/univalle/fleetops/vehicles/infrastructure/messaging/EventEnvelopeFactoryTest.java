package co.edu.univalle.fleetops.vehicles.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class EventEnvelopeFactoryTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-27T15:30:00.123456789Z"),
            ZoneId.of("America/Bogota"));
    private final EventEnvelopeFactory factory = new EventEnvelopeFactory(clock);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void create_buildsTheCourseEnvelope() {
        MDC.put("correlation_id", "6f1c2b7e-3d4a-4b5c-9d6e-7f8a9b0c1d2e");

        EventEnvelope envelope = factory.create("vehicle_created_event", Map.of("vehicle_id", "v-1"));

        assertThat(envelope.eventId()).isNotNull();
        assertThat(envelope.eventType()).isEqualTo("vehicle_created_event");
        assertThat(envelope.source()).isEqualTo("vehicle");
        assertThat(envelope.correlationId()).isEqualTo("6f1c2b7e-3d4a-4b5c-9d6e-7f8a9b0c1d2e");
        assertThat(envelope.data()).containsEntry("vehicle_id", "v-1");
    }

    @Test
    void create_occurredAtIsInGmtMinus5WithMillisecondPrecision() {
        EventEnvelope envelope = factory.create("vehicle_created_event", Map.of());

        assertThat(envelope.occurredAt().toString()).isEqualTo("2026-10-27T10:30:00.123-05:00");
    }

    @Test
    void create_everyEventGetsANewId() {
        UUID first = factory.create("a_event", Map.of()).eventId();
        UUID second = factory.create("a_event", Map.of()).eventId();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void create_withoutCorrelationInTheContext_generatesAUuid() {
        EventEnvelope envelope = factory.create("a_event", Map.of());

        assertThat(UUID.fromString(envelope.correlationId())).isNotNull();
    }

    @Test
    void create_blankCorrelationInTheContext_generatesAUuid() {
        MDC.put("correlation_id", "  ");

        EventEnvelope envelope = factory.create("a_event", Map.of());

        assertThat(UUID.fromString(envelope.correlationId())).isNotNull();
    }
}
