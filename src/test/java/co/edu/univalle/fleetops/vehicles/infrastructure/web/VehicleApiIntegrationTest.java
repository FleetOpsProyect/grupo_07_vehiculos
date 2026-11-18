package co.edu.univalle.fleetops.vehicles.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.univalle.fleetops.vehicles.infrastructure.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class VehicleApiIntegrationTest extends PostgresIntegrationTest {

    private static final String VALID_BODY = """
            {"plate": "abc-123", "vehicle_type": "PICKUP_TRUCK", "brand": "Toyota", "model": "Hilux",
             "year": 2023, "color": "White", "notes": "Assigned to Cali branch", "odometer_km": 15200}""";
    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String postVehicle(String body) throws Exception {
        return mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void postThenGet_createsVehicleAndReturnsContractShape() throws Exception {
        var created = mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicle_id", matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.plate").value("ABC123"))
                .andExpect(jsonPath("$.vehicle_type").value("PICKUP_TRUCK"))
                .andExpect(jsonPath("$.model").value("Hilux"))
                .andExpect(jsonPath("$.year").value(2023))
                .andExpect(jsonPath("$.color").value("White"))
                .andExpect(jsonPath("$.odometer_km").value(15200.0))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.active_blocks", hasSize(0)))
                .andExpect(jsonPath("$.active_route_id").doesNotExist())
                .andExpect(jsonPath("$.deleted").value(false))
                .andExpect(jsonPath("$.created_at", matchesPattern(".*-05:00$")))
                .andReturn().getResponse();
        JsonNode body = objectMapper.readTree(created.getContentAsString());
        String id = body.get("vehicle_id").asText();

        org.junit.jupiter.api.Assertions.assertEquals("/vehicles/" + id, created.getHeader("Location"));
        mvc.perform(get("/vehicles/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_id").value(id))
                .andExpect(jsonPath("$.brand").value("Toyota"))
                .andExpect(jsonPath("$.notes").value("Assigned to Cali branch"))
                .andExpect(jsonPath("$.updated_at", matchesPattern(".*-05:00$")));
    }

    @Test
    void post_duplicatedPlate_returns409WithoutStatusField() throws Exception {
        postVehicle(VALID_BODY);

        String samePlateOtherSpelling = VALID_BODY.replace("abc-123", "ABC 123");

        mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON).content(samePlateOtherSpelling))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLATE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("A vehicle with plate ABC123 already exists"))
                .andExpect(jsonPath("$.path").value("/vehicles"))
                .andExpect(jsonPath("$.timestamp", matchesPattern(".*-05:00$")))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void post_samePlateConcurrently_exactlyOneIsCreatedAndTheOther409() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> request = () -> {
            start.await();
            return mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andReturn().getResponse().getStatus();
        };
        try {
            Future<Integer> first = pool.submit(request);
            Future<Integer> second = pool.submit(request);
            start.countDown();

            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void post_statusInBody_isIgnoredAndVehicleStartsActive() throws Exception {
        String bodyWithStatus = VALID_BODY.replace("\"odometer_km\": 15200",
                "\"odometer_km\": 15200, \"status\": \"BLOCKED\"");

        mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON).content(bodyWithStatus))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.active_blocks", hasSize(0)));
    }

    @Test
    void post_odometerWithTwoDecimals_returns400InvalidFormat() throws Exception {
        mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("15200", "15200.55")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("odometer_km"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }

    @Test
    void post_invalidFields_returns400ListingEveryField() throws Exception {
        String body = """
                {"plate": "12", "vehicle_type": "CAR", "brand": "", "model": "Hilux", "year": 1900,
                 "odometer_km": 15200}""";

        mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[?(@.field=='plate')].code").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors[?(@.field=='brand')].code").value("REQUIRED"))
                .andExpect(jsonPath("$.errors[?(@.field=='year')].code").value("OUT_OF_RANGE"));
    }

    @Test
    void post_unknownVehicleType_returns400OnThatField() throws Exception {
        mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("PICKUP_TRUCK", "SPACESHIP")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("vehicle_type"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }

    @Test
    void post_malformedJson_returns400() throws Exception {
        mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void get_unknownUuid_returns404() throws Exception {
        mvc.perform(get("/vehicles/3fa85f64-5717-4562-b3fc-2c963f66afa6"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/vehicles/3fa85f64-5717-4562-b3fc-2c963f66afa6"));
    }

    @Test
    void get_idThatIsNotUuid_returns404() throws Exception {
        mvc.perform(get("/vehicles/not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"));
    }

    @Test
    void request_withCorrelationId_echoesItAndGeneratesOneWhenMissingOrInvalid() throws Exception {
        String incoming = "6f1c2b7e-3d4a-4b5c-9d6e-7f8a9b0c1d2e";

        mvc.perform(get("/vehicles/not-a-uuid").header("X-Correlation-Id", incoming))
                .andExpect(header().string("X-Correlation-Id", incoming));
        mvc.perform(get("/vehicles/not-a-uuid"))
                .andExpect(header().string("X-Correlation-Id", matchesPattern(UUID_PATTERN)));
        mvc.perform(get("/vehicles/not-a-uuid").header("X-Correlation-Id", "garbage"))
                .andExpect(header().string("X-Correlation-Id", matchesPattern(UUID_PATTERN)));
    }

    @Test
    void health_reportsUpWithDatabase() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    void swagger_apiDocsAreServed() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/vehicles']").exists())
                .andExpect(jsonPath("$.paths['/vehicles/{id}']").exists());
    }

    @Test
    void get_vehicleInMaintenance_returns200WithActiveBlocks() throws Exception {
        JsonNode created = objectMapper.readTree(postVehicle(VALID_BODY));
        String id = created.get("vehicle_id").asText();
        String maintenanceId = "11111111-1111-1111-1111-111111111111";

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/vehicles/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"IN_MAINTENANCE\", \"maintenance_id\": \"" + maintenanceId + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/vehicles/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_id").value(id))
                .andExpect(jsonPath("$.status").value("IN_MAINTENANCE"))
                .andExpect(jsonPath("$.active_blocks", hasSize(1)))
                .andExpect(jsonPath("$.active_blocks[0].type").value("MAINTENANCE"))
                .andExpect(jsonPath("$.active_blocks[0].source_id").value(maintenanceId));
    }

    @Test
    void put_validPayload_updatesEditableFieldsWithoutChangingIdentityOrStatus() throws Exception {
        JsonNode created = objectMapper.readTree(postVehicle(VALID_BODY));
        String id = created.get("vehicle_id").asText();
        String maintenanceId = "11111111-1111-1111-1111-111111111111";

        // Pasamos el vehículo a IN_MAINTENANCE para verificar que ni el estado ni los bloqueos cambian
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/vehicles/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"IN_MAINTENANCE\", \"maintenance_id\": \"" + maintenanceId + "\"}"))
                .andExpect(status().isOk());

        String updateBody = """
                {"brand": "Ford", "model": "Ranger", "year": 2025, "color": "Black", "notes": "Updated notes"}""";

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/vehicles/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_id").value(id))
                .andExpect(jsonPath("$.plate").value("ABC123"))
                .andExpect(jsonPath("$.vehicle_type").value("PICKUP_TRUCK"))
                .andExpect(jsonPath("$.brand").value("Ford"))
                .andExpect(jsonPath("$.model").value("Ranger"))
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.color").value("Black"))
                .andExpect(jsonPath("$.notes").value("Updated notes"))
                .andExpect(jsonPath("$.odometer_km").value(15200.0))
                .andExpect(jsonPath("$.status").value("IN_MAINTENANCE"))
                .andExpect(jsonPath("$.active_blocks", hasSize(1)))
                .andExpect(jsonPath("$.created_at", matchesPattern(".*-05:00$")))
                .andExpect(jsonPath("$.updated_at", matchesPattern(".*-05:00$")));
    }

    @Test
    void put_invalidFields_returns400ListingEveryField() throws Exception {
        JsonNode created = objectMapper.readTree(postVehicle(VALID_BODY));
        String id = created.get("vehicle_id").asText();

        String invalidUpdate = """
                {"brand": "T", "model": "", "year": 1979, "color": "1234567890123456789012345678901"}""";

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/vehicles/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidUpdate))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.errors", hasSize(4)));
    }

    @Test
    void put_nonEditableFields_returns400NotEditableAndDoesNotModifyVehicle() throws Exception {
        JsonNode created = objectMapper.readTree(postVehicle(VALID_BODY));
        String id = created.get("vehicle_id").asText();

        String bodyWithForbiddenFields = """
                {"brand": "Ford", "model": "Ranger", "year": 2025, "plate": "XYZ999",
                 "vehicle_type": "CAR", "odometer_km": 99999, "status": "IN_MAINTENANCE"}""";

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/vehicles/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithForbiddenFields))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.errors", hasSize(4)))
                .andExpect(jsonPath("$.errors[?(@.field=='plate')].code").value("NOT_EDITABLE"))
                .andExpect(jsonPath("$.errors[?(@.field=='vehicle_type')].code").value("NOT_EDITABLE"))
                .andExpect(jsonPath("$.errors[?(@.field=='odometer_km')].code").value("NOT_EDITABLE"))
                .andExpect(jsonPath("$.errors[?(@.field=='status')].code").value("NOT_EDITABLE"));

        // Verificamos que en BD no cambió nada
        mvc.perform(get("/vehicles/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand").value("Toyota"))
                .andExpect(jsonPath("$.model").value("Hilux"))
                .andExpect(jsonPath("$.plate").value("ABC123"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void put_unknownUuid_returns404() throws Exception {
        String validUpdate = """
                {"brand": "Ford", "model": "Ranger", "year": 2025}""";

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/vehicles/3fa85f64-5717-4562-b3fc-2c963f66afa6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdate))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    void put_idThatIsNotUuid_returns404() throws Exception {
        String validUpdate = """
                {"brand": "Ford", "model": "Ranger", "year": 2025}""";

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/vehicles/not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdate))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").doesNotExist());
    }

}