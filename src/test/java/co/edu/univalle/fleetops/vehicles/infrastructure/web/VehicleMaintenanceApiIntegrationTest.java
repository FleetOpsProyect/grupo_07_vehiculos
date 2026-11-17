package co.edu.univalle.fleetops.vehicles.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.univalle.fleetops.vehicles.infrastructure.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class VehicleMaintenanceApiIntegrationTest extends PostgresIntegrationTest {

    private static final String VEHICLE_BODY = """
            {"plate": "%s", "vehicle_type": "PICKUP_TRUCK", "brand": "Toyota", "model": "Hilux",
             "year": 2023, "color": "White", "odometer_km": 15200}""";
    private static final String UNKNOWN_ID = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
    private static final String MAINTENANCE_CAUSE = "MAINTENANCE_STARTED";

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

    private ResultActions putStatus(String vehicleId, String body) throws Exception {
        return mvc.perform(put("/vehicles/" + vehicleId + "/status")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String maintenanceBody(String status, UUID maintenanceId) {
        return "{\"status\": \"%s\", \"maintenance_id\": \"%s\"}".formatted(status, maintenanceId);
    }

    private int historyRows(String vehicleId, String cause) {
        return jdbc.queryForObject("SELECT count(*) FROM status_history WHERE vehicle_id = ?::uuid AND cause = ?",
                Integer.class, vehicleId, cause);
    }

    private int blockRows(String vehicleId) {
        return jdbc.queryForObject("SELECT count(*) FROM vehicle_block WHERE vehicle_id = ?::uuid",
                Integer.class, vehicleId);
    }

    @Test
    void put_activeVehicle_returns200WithMaintenanceBlockAndOneHistoryRow() throws Exception {
        String id = createVehicle("MNT001");
        UUID maintenanceId = UUID.randomUUID();

        putStatus(id, maintenanceBody("IN_MAINTENANCE", maintenanceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_id").value(id))
                .andExpect(jsonPath("$.status").value("IN_MAINTENANCE"))
                .andExpect(jsonPath("$.active_blocks", hasSize(1)))
                .andExpect(jsonPath("$.active_blocks[0].block_key").value("MAINTENANCE:" + maintenanceId))
                .andExpect(jsonPath("$.active_blocks[0].type").value("MAINTENANCE"))
                .andExpect(jsonPath("$.active_blocks[0].source_id").value(maintenanceId.toString()))
                .andExpect(jsonPath("$.updated_at", matchesPattern(".*-05:00$")));

        mvc.perform(get("/vehicles/" + id))
                .andExpect(jsonPath("$.status").value("IN_MAINTENANCE"))
                .andExpect(jsonPath("$.active_blocks", hasSize(1)));
        assertThat(historyRows(id, MAINTENANCE_CAUSE)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT source_id FROM status_history WHERE vehicle_id = ?::uuid AND cause = ?",
                String.class, id, MAINTENANCE_CAUSE)).isEqualTo(maintenanceId.toString());
    }

    @Test
    void put_sameMaintenanceIdTwice_returns200WithSingleBlockAndNoNewHistoryRow() throws Exception {
        String id = createVehicle("MNT002");
        String body = maintenanceBody("IN_MAINTENANCE", UUID.randomUUID());
        putStatus(id, body).andExpect(status().isOk());
        String stored = mvc.perform(get("/vehicles/" + id)).andReturn().getResponse().getContentAsString();

        String second = putStatus(id, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_MAINTENANCE"))
                .andExpect(jsonPath("$.active_blocks", hasSize(1)))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(second).get("updated_at"))
                .isEqualTo(objectMapper.readTree(stored).get("updated_at"));
        assertThat(blockRows(id)).isEqualTo(1);
        assertThat(historyRows(id, MAINTENANCE_CAUSE)).isEqualTo(1);
    }

    @Test
    void put_missingMaintenanceId_returns400Required() throws Exception {
        String id = createVehicle("MNT003");

        putStatus(id, "{\"status\": \"IN_MAINTENANCE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("maintenance_id"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void put_maintenanceIdThatIsNotUuid_returns400InvalidFormat() throws Exception {
        String id = createVehicle("MNT004");

        putStatus(id, "{\"status\": \"IN_MAINTENANCE\", \"maintenance_id\": \"not-a-uuid\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("maintenance_id"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }

    @Test
    void put_missingStatus_returns400Required() throws Exception {
        String id = createVehicle("MNT005");

        putStatus(id, "{\"maintenance_id\": \"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void put_unknownStatusValue_returns400InvalidFormatOnThatField() throws Exception {
        String id = createVehicle("MNT006");

        putStatus(id, maintenanceBody("FOO", UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }

    @Test
    void put_emptyBody_returns400ListingBothFields() throws Exception {
        String id = createVehicle("MNT007");

        putStatus(id, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[?(@.field=='status')].code").value("REQUIRED"))
                .andExpect(jsonPath("$.errors[?(@.field=='maintenance_id')].code").value("REQUIRED"));
    }

    @Test
    void put_malformedJson_returns400() throws Exception {
        String id = createVehicle("MNT008");

        putStatus(id, "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void put_invalidBodyOnUnknownVehicle_returns400BeforeNotFound() throws Exception {
        putStatus(UNKNOWN_ID, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void put_unknownUuid_returns404WithoutErrorsField() throws Exception {
        putStatus(UNKNOWN_ID, maintenanceBody("IN_MAINTENANCE", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/vehicles/" + UNKNOWN_ID + "/status"))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void put_idThatIsNotUuid_returns404() throws Exception {
        putStatus("not-a-uuid", maintenanceBody("IN_MAINTENANCE", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"));
    }

    @Test
    void put_deletedVehicle_returns404() throws Exception {
        String id = createVehicle("MNT009");
        jdbc.update("UPDATE vehicle SET deleted = TRUE, status = 'DELETED' WHERE vehicle_id = ?::uuid", id);

        putStatus(id, maintenanceBody("IN_MAINTENANCE", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"));
    }

    @Test
    void put_otherStatusOnExistingVehicle_returns422AndChangesNothing() throws Exception {
        String id = createVehicle("MNT010");

        putStatus(id, maintenanceBody("ACTIVE", UUID.randomUUID()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("STATUS_NOT_ALLOWED"))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist());

        mvc.perform(get("/vehicles/" + id)).andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(historyRows(id, MAINTENANCE_CAUSE)).isZero();
    }

    @Test
    void put_otherStatusOnUnknownVehicle_returns404BeforeUnprocessable() throws Exception {
        putStatus(UNKNOWN_ID, maintenanceBody("ACTIVE", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"));
    }

    @ParameterizedTest
    @CsvSource({"ON_ROUTE,MNT012", "BLOCKED,MNT013", "INACTIVE_EXPIRED_DOCUMENTS,MNT014"})
    void put_vehicleNotActive_returns409WithItsCurrentStatusAndChangesNothing(String currentStatus, String plate)
            throws Exception {
        String id = createVehicle(plate);
        jdbc.update("UPDATE vehicle SET status = ? WHERE vehicle_id = ?::uuid", currentStatus, id);

        putStatus(id, maintenanceBody("IN_MAINTENANCE", UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.message", containsString(currentStatus)));

        assertThat(blockRows(id)).isZero();
        assertThat(historyRows(id, MAINTENANCE_CAUSE)).isZero();
        mvc.perform(get("/vehicles/" + id)).andExpect(jsonPath("$.status").value(currentStatus));
    }

    @Test
    void put_anotherMaintenanceIdWhenAlreadyInMaintenance_returns409WithCurrentStatus() throws Exception {
        String id = createVehicle("MNT011");
        putStatus(id, maintenanceBody("IN_MAINTENANCE", UUID.randomUUID())).andExpect(status().isOk());

        putStatus(id, maintenanceBody("IN_MAINTENANCE", UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.message", containsString("IN_MAINTENANCE")))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist());

        assertThat(blockRows(id)).isEqualTo(1);
        assertThat(historyRows(id, MAINTENANCE_CAUSE)).isEqualTo(1);
    }
}
