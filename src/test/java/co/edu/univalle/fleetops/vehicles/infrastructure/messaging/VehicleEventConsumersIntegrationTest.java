package co.edu.univalle.fleetops.vehicles.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.univalle.fleetops.vehicles.infrastructure.support.LocalStackSupport;
import co.edu.univalle.fleetops.vehicles.infrastructure.support.MessagingIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Los consumidores del contrato de punta a punta: mensaje en {@code vehicles_queue} (LocalStack), listener,
 * consumidor, PostgreSQL y los eventos que deja en el outbox.
 */
class VehicleEventConsumersIntegrationTest extends MessagingIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(40);
    private static final String VEHICLE_BODY = """
            {"plate": "%s", "vehicle_type": "PICKUP_TRUCK", "brand": "Toyota", "model": "Hilux",
             "year": 2023, "color": "White", "odometer_km": 15200}""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private String createVehicle(String plate) throws Exception {
        String response = mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content(VEHICLE_BODY.formatted(plate)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("vehicle_id").asText();
    }

    private static void send(String eventType, String data) {
        String body = "{\"event_id\":\"" + UUID.randomUUID() + "\",\"event_type\":\"" + eventType + "\","
                + "\"occurred_at\":\"2026-10-27T09:30:02-05:00\",\"source\":\"test\","
                + "\"correlation_id\":\"" + UUID.randomUUID() + "\",\"data\":" + data + "}";
        LocalStackSupport.sqs().sendMessage(request -> request.queueUrl(LocalStackSupport.queueUrl())
                .messageBody(body));
    }

    private String statusOf(String vehicleId) {
        return jdbc.queryForObject("SELECT status FROM vehicle WHERE vehicle_id = ?::uuid", String.class, vehicleId);
    }

    private void awaitStatus(String vehicleId, String expected) {
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(statusOf(vehicleId)).isEqualTo(expected));
    }

    private int processedCount() {
        return jdbc.queryForObject("SELECT count(*) FROM processed_events", Integer.class);
    }

    private List<String> statusChanges(String vehicleId) {
        return jdbc.queryForList("""
                SELECT (payload -> 'data' ->> 'previous_status') || '>' || (payload -> 'data' ->> 'status')
                    || ' ' || (payload -> 'data' -> 'cause' ->> 'type')
                    || ':' || (payload -> 'data' -> 'cause' ->> 'action')
                    || ' v' || (payload -> 'data' ->> 'version_number')
                FROM outbox_events
                WHERE aggregate_id = ?::uuid AND event_type = 'vehicle_status_changed_event'
                ORDER BY CAST(payload -> 'data' ->> 'version_number' AS integer)""", String.class, vehicleId);
    }

    private int historyRows(String vehicleId) {
        return jdbc.queryForObject("SELECT count(*) FROM status_history WHERE vehicle_id = ?::uuid "
                + "AND cause <> 'VEHICLE_CREATED'", Integer.class, vehicleId);
    }

    @Test
    void incidentAndCorrectiveMaintenance_blockAndReleaseTheVehicle() throws Exception {
        String id = createVehicle("CON001");
        String incidentId = UUID.randomUUID().toString();

        send("severe_incident_reported_event", """
                {"incident_id": "%s", "vehicle_id": "%s", "severity": "severe"}""".formatted(incidentId, id));
        awaitStatus(id, "BLOCKED");
        send("maintenance_completed_event", """
                {"maintenance_order_id": "%s", "vehicle_id": "%s", "maintenance_type": "CORRECTIVE",
                 "incident_id": "%s"}""".formatted(UUID.randomUUID(), id, incidentId));
        awaitStatus(id, "ACTIVE");

        assertThat(statusChanges(id)).containsExactly(
                "ACTIVE>BLOCKED INCIDENT:BLOCK_ADDED v1",
                "BLOCKED>ACTIVE MAINTENANCE:BLOCK_REMOVED v2");
        assertThat(historyRows(id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vehicle_block WHERE vehicle_id = ?::uuid",
                Integer.class, id)).isZero();
    }

    @Test
    void expiredAndRenewedDocument_deactivateAndReactivateTheVehicle() throws Exception {
        String id = createVehicle("CON002");

        send("document_expired_event", """
                {"document_id": "doc-1", "document_type": "SOAT", "owner_type": "VEHICLE", "owner_id": "%s",
                 "expiration_date": "2026-10-20"}""".formatted(id));
        awaitStatus(id, "INACTIVE_EXPIRED_DOCUMENTS");
        send("document_renewed_event", """
                {"document_id": "doc-2", "previous_document_id": "doc-1", "origin": "RENEWAL", "document_type": "SOAT",
                 "owner_type": "VEHICLE", "owner_id": "%s"}""".formatted(id));
        awaitStatus(id, "ACTIVE");

        assertThat(statusChanges(id)).containsExactly(
                "ACTIVE>INACTIVE_EXPIRED_DOCUMENTS DOCUMENT:BLOCK_ADDED v1",
                "INACTIVE_EXPIRED_DOCUMENTS>ACTIVE DOCUMENT:BLOCK_REMOVED v2");
    }

    @Test
    void routeStartedAndFinishedTwice_addsTheDistanceOnlyOnce() throws Exception {
        String id = createVehicle("CON003");
        String routeId = UUID.randomUUID().toString();
        String finished = """
                {"route_id": "%s", "vehicle_id": "%s", "distance_km": 15.5, "route_status": "COMPLETED"}"""
                .formatted(routeId, id);

        send("route_started_event", "{\"route_id\": \"%s\", \"vehicle_id\": \"%s\"}".formatted(routeId, id));
        awaitStatus(id, "ON_ROUTE");
        send("route_finished_event", finished);
        send("route_finished_event", finished);
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(processedCount()).isEqualTo(3));

        assertThat(statusOf(id)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT odometer_km FROM vehicle WHERE vehicle_id = ?::uuid",
                BigDecimal.class, id)).isEqualByComparingTo("15215.5");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM applied_route WHERE route_id = ?::uuid",
                Integer.class, routeId)).isEqualTo(1);
        assertThat(jdbc.queryForList("""
                SELECT CAST(payload -> 'data' ->> 'odometer_km' AS text) FROM outbox_events
                WHERE aggregate_id = ?::uuid AND event_type = 'vehicle_odometer_updated_event'""", String.class, id))
                .containsExactly("15215.5");
        assertThat(statusChanges(id)).containsExactly(
                "ACTIVE>ON_ROUTE ROUTE:ROUTE_STARTED v1",
                "ON_ROUTE>ACTIVE ROUTE:ROUTE_FINISHED v2");
    }

    @Test
    void eventForUnknownVehicle_isRecordedAsProcessedWithoutChanges() {
        send("severe_incident_reported_event", """
                {"incident_id": "inc-1", "vehicle_id": "%s"}""".formatted(UUID.randomUUID()));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(processedCount()).isEqualTo(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isZero();
    }
}
