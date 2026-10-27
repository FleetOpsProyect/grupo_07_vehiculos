package co.edu.univalle.fleetops.vehicles.infrastructure.web;

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
                .andExpect(jsonPath("$.plate").value("ABC123"))
                .andExpect(jsonPath("$.vehicle_type").value("PICKUP_TRUCK"))
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
}
