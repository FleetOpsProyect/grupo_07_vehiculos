package co.edu.univalle.fleetops.vehicles.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.univalle.fleetops.vehicles.application.exception.AssignmentServiceUnavailableException;
import co.edu.univalle.fleetops.vehicles.application.port.out.ActiveAssignmentsPort;
import co.edu.univalle.fleetops.vehicles.infrastructure.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** DELETE /vehicles/{id} con PostgreSQL real; Asignaciones (G2) se simula. */
@AutoConfigureMockMvc
class VehicleDeleteApiIntegrationTest extends PostgresIntegrationTest {

    private static final String VEHICLE_BODY = """
            {"plate": "%s", "vehicle_type": "PICKUP_TRUCK", "brand": "Toyota", "model": "Hilux",
             "year": 2023, "color": "White", "odometer_km": 15200}""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private ActiveAssignmentsPort assignments;

    private UUID createVehicle(String plate) throws Exception {
        String response = mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content(VEHICLE_BODY.formatted(plate)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("vehicle_id").asText());
    }

    private int count(String sql, UUID vehicleId) {
        return jdbc.queryForObject(sql, Integer.class, vehicleId);
    }

    private int deletedEvents(UUID vehicleId) {
        return count("SELECT count(*) FROM outbox_events WHERE aggregate_id = ? "
                + "AND event_type = 'vehicle_deleted_event'", vehicleId);
    }

    private String storedStatus(UUID vehicleId) {
        return jdbc.queryForObject("SELECT status FROM vehicle WHERE vehicle_id = ?", String.class, vehicleId);
    }

    @Test
    void delete_withoutActiveAssignments_returns200MarksDeletedAndRecordsHistoryAndEvent() throws Exception {
        UUID id = createVehicle("DEL001");
        when(assignments.countActiveAssignments(id)).thenReturn(0);

        mvc.perform(delete("/vehicles/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_id").value(id.toString()))
                .andExpect(jsonPath("$.plate").value("DEL001"))
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.updated_at").value(endsWith("-05:00")));

        assertThat(storedStatus(id)).isEqualTo("DELETED");
        assertThat(count("SELECT count(*) FROM status_history WHERE vehicle_id = ? AND cause = 'VEHICLE_DELETED'",
                id)).isEqualTo(1);
        String payload = jdbc.queryForObject("SELECT CAST(payload AS text) FROM outbox_events "
                + "WHERE aggregate_id = ? AND event_type = 'vehicle_deleted_event'", String.class, id);
        JsonNode data = objectMapper.readTree(payload).get("data");
        assertThat(data.get("vehicle_id").asText()).isEqualTo(id.toString());
        assertThat(data.get("plate").asText()).isEqualTo("DEL001");
        assertThat(data.get("deleted_at").asText()).endsWith("-05:00");
        assertThat(data.get("version_number").asLong()).isEqualTo(1);
    }

    @Test
    void delete_alreadyDeleted_returns200WithoutAskingAssignmentsOrRecordingAnotherEvent() throws Exception {
        UUID id = createVehicle("DEL002");
        when(assignments.countActiveAssignments(id)).thenReturn(0);
        mvc.perform(delete("/vehicles/" + id)).andExpect(status().isOk());

        mvc.perform(delete("/vehicles/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETED"));

        verify(assignments).countActiveAssignments(id);
        assertThat(deletedEvents(id)).isEqualTo(1);
    }

    @Test
    void delete_withActiveAssignments_returns409AndChangesNothing() throws Exception {
        UUID id = createVehicle("DEL003");
        when(assignments.countActiveAssignments(id)).thenReturn(2);

        mvc.perform(delete("/vehicles/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_HAS_ACTIVE_ASSIGNMENTS"));

        assertThat(storedStatus(id)).isEqualTo("ACTIVE");
        assertThat(deletedEvents(id)).isZero();
    }

    @Test
    void delete_assignmentsServiceDown_returns503AndChangesNothing() throws Exception {
        UUID id = createVehicle("DEL004");
        when(assignments.countActiveAssignments(id))
                .thenThrow(new AssignmentServiceUnavailableException(new RuntimeException("timeout")));

        mvc.perform(delete("/vehicles/" + id))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_SERVICE_UNAVAILABLE"));

        assertThat(storedStatus(id)).isEqualTo("ACTIVE");
        assertThat(deletedEvents(id)).isZero();
    }

    @Test
    void delete_unknownVehicle_returns404WithoutAskingAssignments() throws Exception {
        mvc.perform(delete("/vehicles/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"));

        verify(assignments, never()).countActiveAssignments(any());
    }

    @Test
    void delete_idNotUuid_returns404() throws Exception {
        mvc.perform(delete("/vehicles/not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"));
    }
}
