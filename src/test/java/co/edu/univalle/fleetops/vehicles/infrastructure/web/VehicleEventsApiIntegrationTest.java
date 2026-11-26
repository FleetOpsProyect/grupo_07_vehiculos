package co.edu.univalle.fleetops.vehicles.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.univalle.fleetops.vehicles.infrastructure.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Los endpoints dejan su evento en el outbox, en la misma transacción. Sin topic configurado el
 * publicador está apagado, así que los eventos quedan PENDING y se pueden leer.
 */
@AutoConfigureMockMvc
class VehicleEventsApiIntegrationTest extends PostgresIntegrationTest {

    private static final String VEHICLE_BODY = """
            {"plate": "%s", "vehicle_type": "PICKUP_TRUCK", "brand": "Toyota", "model": "Hilux",
             "year": 2023, "color": "White", "odometer_km": 15200}""";
    private static final String CORRELATION_ID = "6f1c2b7e-3d4a-4b5c-9d6e-7f8a9b0c1d2e";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private String createVehicle(String plate) throws Exception {
        String response = mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", CORRELATION_ID)
                        .content(VEHICLE_BODY.formatted(plate)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("vehicle_id").asText();
    }

    private void updateVehicle(String id, String body) throws Exception {
        mvc.perform(put("/vehicles/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private List<JsonNode> events(String vehicleId) {
        return jdbc.queryForList("SELECT CAST(payload AS text) FROM outbox_events WHERE aggregate_id = ?::uuid "
                        + "ORDER BY CAST(payload -> 'data' ->> 'version_number' AS integer), created_at",
                String.class, vehicleId).stream().map(this::read).toList();
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private long storedVersion(String vehicleId) {
        return jdbc.queryForObject("SELECT version_number FROM vehicle WHERE vehicle_id = ?::uuid",
                Long.class, vehicleId);
    }

    @Test
    void post_recordsVehicleCreatedEventWithTheCourseEnvelopeAndVersionZero() throws Exception {
        String id = createVehicle("EVT001");

        List<JsonNode> events = events(id);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(UUID.fromString(event.get("event_id").asText())).isNotNull();
            assertThat(event.get("event_type").asText()).isEqualTo("vehicle_created_event");
            assertThat(event.get("occurred_at").asText()).endsWith("-05:00");
            assertThat(event.get("source").asText()).isEqualTo("vehicle");
            assertThat(event.get("correlation_id").asText()).isEqualTo(CORRELATION_ID);
            JsonNode data = event.get("data");
            assertThat(data.get("vehicle_id").asText()).isEqualTo(id);
            assertThat(data.get("plate").asText()).isEqualTo("EVT001");
            assertThat(data.get("vehicle_type").asText()).isEqualTo("PICKUP_TRUCK");
            assertThat(data.get("odometer_km").decimalValue()).isEqualByComparingTo("15200.0");
            assertThat(data.get("notes").isNull()).isTrue();
            assertThat(data.get("status").asText()).isEqualTo("ACTIVE");
            assertThat(data.get("version_number").asLong()).isZero();
        });
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE aggregate_id = ?::uuid",
                String.class, id)).isEqualTo("PENDING");
        assertThat(storedVersion(id)).isZero();
    }

    @Test
    void post_duplicatedPlate_rollsBackWithoutRecordingAnotherEvent() throws Exception {
        createVehicle("EVT002");

        mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON).content(VEHICLE_BODY.formatted("EVT002")))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isEqualTo(1);
    }

    @Test
    void put_changedFields_recordsVehicleUpdatedEventWithChangedFieldsAndVersionOne() throws Exception {
        String id = createVehicle("EVT003");

        updateVehicle(id, """
                {"brand": "Toyota", "model": "Hilux SR", "year": 2023, "color": "Silver"}""");

        List<JsonNode> events = events(id);
        assertThat(events).hasSize(2);
        JsonNode updated = events.get(1);
        assertThat(updated.get("event_type").asText()).isEqualTo("vehicle_updated_event");
        assertThat(updated.at("/data/changed_fields").toString()).isEqualTo("[\"model\",\"color\"]");
        assertThat(updated.at("/data/model").asText()).isEqualTo("Hilux SR");
        assertThat(updated.at("/data/version_number").asLong()).isEqualTo(1);
        assertThat(storedVersion(id)).isEqualTo(1);
    }

    @Test
    void delete_assignmentsServiceUnreachable_returns503AndKeepsTheVehicleWithoutEvent() throws Exception {
        String id = createVehicle("EVT006");

        mvc.perform(delete("/vehicles/" + id))
                .andExpect(status().isServiceUnavailable());

        assertThat(events(id)).extracting(event -> event.get("event_type").asText())
                .containsExactly("vehicle_created_event");
        assertThat(storedVersion(id)).isZero();
    }

    @Test
    void put_sameData_doesNotRecordAnEventNorRaiseTheVersion() throws Exception {
        String id = createVehicle("EVT004");

        updateVehicle(id, """
                {"brand": "Toyota", "model": "Hilux", "year": 2023, "color": "White"}""");

        assertThat(events(id)).hasSize(1);
        assertThat(storedVersion(id)).isZero();
    }

    @Test
    void putStatus_recordsStatusChangedEventAfterTheEditWithTheNextVersion() throws Exception {
        String id = createVehicle("EVT005");
        updateVehicle(id, """
                {"brand": "Toyota", "model": "Hilux", "year": 2024, "color": "White"}""");
        UUID maintenanceId = UUID.randomUUID();

        String body = "{\"status\": \"IN_MAINTENANCE\", \"maintenance_id\": \"%s\"}".formatted(maintenanceId);

        mvc.perform(put("/vehicles/" + id + "/status").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        List<JsonNode> events = events(id);
        assertThat(events).extracting(event -> event.get("event_type").asText())
                .containsExactly("vehicle_created_event", "vehicle_updated_event", "vehicle_status_changed_event");
        JsonNode data = events.get(2).get("data");
        assertThat(data.get("previous_status").asText()).isEqualTo("ACTIVE");
        assertThat(data.get("status").asText()).isEqualTo("IN_MAINTENANCE");
        assertThat(data.at("/cause/type").asText()).isEqualTo("MAINTENANCE");
        assertThat(data.at("/cause/action").asText()).isEqualTo("BLOCK_ADDED");
        assertThat(data.at("/cause/source_id").asText()).isEqualTo(maintenanceId.toString());
        assertThat(data.at("/active_blocks/0/block_key").asText()).isEqualTo("MAINTENANCE:" + maintenanceId);
        assertThat(data.get("is_assignable").asBoolean()).isFalse();
        assertThat(data.get("version_number").asLong()).isEqualTo(2);
        assertThat(storedVersion(id)).isEqualTo(2);
    }
}
