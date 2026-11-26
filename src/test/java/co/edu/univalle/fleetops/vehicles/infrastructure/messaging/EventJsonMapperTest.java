package co.edu.univalle.fleetops.vehicles.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.univalle.fleetops.vehicles.application.event.IncomingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EventJsonMapperTest {

    private final EventJsonMapper mapper = new EventJsonMapper();

    @Test
    void toJson_envelope_usesTheCourseFieldsInSnakeCaseAndGmtMinus5() throws Exception {
        var envelope = new EventEnvelope(UUID.fromString("0b9a7c55-2f1e-4d3a-8b6c-5e4f3a2b1c0d"),
                "vehicle_created_event", OffsetDateTime.parse("2026-10-27T10:30:00-05:00"), "vehicle",
                "6f1c2b7e-3d4a-4b5c-9d6e-7f8a9b0c1d2e", Map.of("vehicle_id", "v-1", "odometer_km", 15200.5));

        JsonNode json = new ObjectMapper().readTree(mapper.toJson(envelope));

        assertThat(json.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("event_id", "event_type", "occurred_at", "source", "correlation_id",
                        "data");
        assertThat(json.path("event_id").asText()).isEqualTo("0b9a7c55-2f1e-4d3a-8b6c-5e4f3a2b1c0d");
        assertThat(json.path("occurred_at").asText()).isEqualTo("2026-10-27T10:30:00-05:00");
        assertThat(json.path("source").asText()).isEqualTo("vehicle");
        assertThat(json.path("data").path("odometer_km").asDouble()).isEqualTo(15200.5);
    }

    @Test
    void parse_rawEnvelope_returnsTheEvent() {
        String body = "{\"event_id\":\"abc-1\",\"event_type\":\"document_expired_event\","
                + "\"correlation_id\":\"corr-9\",\"data\":{\"owner_type\":\"VEHICLE\",\"year\":2023}}";

        IncomingEvent event = mapper.parse(body);

        assertThat(event.eventId()).isEqualTo("abc-1");
        assertThat(event.eventType()).isEqualTo("document_expired_event");
        assertThat(event.correlationId()).isEqualTo("corr-9");
        assertThat(event.data()).containsEntry("owner_type", "VEHICLE").containsEntry("year", 2023);
    }

    @Test
    void parse_envelopeWithoutCorrelationIdOrData_usesNullAndAnEmptyMap() {
        IncomingEvent event = mapper.parse("{\"event_id\":\"abc-1\",\"event_type\":\"x_event\"}");

        assertThat(event.correlationId()).isNull();
        assertThat(event.data()).isEmpty();
    }

    @Test
    void parse_dataThatIsNotAnObject_isTreatedAsEmpty() {
        IncomingEvent event = mapper.parse("{\"event_id\":\"1\",\"event_type\":\"x_event\",\"data\":[1,2]}");

        assertThat(event.data()).isEmpty();
    }

    @Test
    void parse_snsNotification_unwrapsTheEnvelopeFromMessage() throws Exception {
        String inner = "{\"event_id\":\"abc-1\",\"event_type\":\"x_event\",\"data\":{\"k\":\"v\"}}";
        String body = new ObjectMapper().writeValueAsString(Map.of("Type", "Notification", "Message", inner));

        IncomingEvent event = mapper.parse(body);

        assertThat(event.eventId()).isEqualTo("abc-1");
        assertThat(event.data()).containsEntry("k", "v");
    }

    @Test
    void parse_numericEventId_isReadAsText() {
        IncomingEvent event = mapper.parse("{\"event_id\":12345,\"event_type\":\"x_event\"}");

        assertThat(event.eventId()).isEqualTo("12345");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"event_type\":\"x_event\"}", "{\"event_id\":\"1\"}",
        "{\"event_id\":\"  \",\"event_type\":\"x_event\"}", "{\"event_id\":\"1\",\"event_type\":\"\"}",
        "{\"event_id\":{\"a\":1},\"event_type\":\"x_event\"}", "{}"})
    void parse_missingEventIdOrType_isRejected(String body) {
        assertThatThrownBy(() -> mapper.parse(body)).isInstanceOf(InvalidEventMessageException.class)
                .hasMessageContaining("event_id or event_type");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "[1,2]", "\"text\"", "", "null", "{\"event_id\":"})
    void parse_bodyThatIsNotAJsonObject_isRejected(String body) {
        assertThatThrownBy(() -> mapper.parse(body)).isInstanceOf(InvalidEventMessageException.class);
    }
}
