package co.edu.univalle.fleetops.vehicles.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.univalle.fleetops.vehicles.infrastructure.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class VehicleListApiIntegrationTest extends PostgresIntegrationTest {

    private static final String VEHICLE_BODY = """
            {"plate": "%s", "vehicle_type": "%s", "brand": "Toyota", "model": "Hilux",
             "year": 2023, "color": "White", "odometer_km": 15200.5}""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private String createVehicle(String plate, String vehicleType) throws Exception {
        String response = mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content(VEHICLE_BODY.formatted(plate, vehicleType)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("vehicle_id").asText();
    }

    private void forceStatus(String vehicleId, String newStatus) {
        jdbc.update("UPDATE vehicle SET status = ? WHERE vehicle_id = ?::uuid", newStatus, vehicleId);
    }

    private void markDeleted(String vehicleId) {
        jdbc.update("UPDATE vehicle SET deleted = TRUE, status = 'DELETED' WHERE vehicle_id = ?::uuid", vehicleId);
    }

    private List<String> platesOf(String responseBody) throws Exception {
        List<String> plates = new ArrayList<>();
        for (JsonNode row : objectMapper.readTree(responseBody).get("content")) {
            plates.add(row.get("plate").asText());
        }
        return plates;
    }

    @Test
    void list_withoutParameters_returnsDefaultPageWithContractShapeAndSummary() throws Exception {
        String id = createVehicle("LST001", "CAR");
        String inMaintenance = createVehicle("LST002", "PICKUP_TRUCK");
        String deleted = createVehicle("LST003", "SPECIALIZED_VEHICLE");
        forceStatus(inMaintenance, "IN_MAINTENANCE");
        markDeleted(deleted);

        mvc.perform(get("/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].plate", containsInAnyOrder("LST001", "LST002")))
                .andExpect(jsonPath("$.content[?(@.plate == 'LST001')].vehicle_id").value(id))
                .andExpect(jsonPath("$.content[?(@.plate == 'LST001')].vehicle_type").value("CAR"))
                .andExpect(jsonPath("$.content[?(@.plate == 'LST001')].brand").value("Toyota"))
                .andExpect(jsonPath("$.content[?(@.plate == 'LST001')].model").value("Hilux"))
                .andExpect(jsonPath("$.content[?(@.plate == 'LST001')].year").value(2023))
                .andExpect(jsonPath("$.content[?(@.plate == 'LST001')].odometer_km").value(15200.5))
                .andExpect(jsonPath("$.content[?(@.plate == 'LST001')].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].active_blocks").doesNotExist())
                .andExpect(jsonPath("$.content[0].created_at").doesNotExist())
                .andExpect(jsonPath("$.summary.ACTIVE").value(1))
                .andExpect(jsonPath("$.summary.ON_ROUTE").value(0))
                .andExpect(jsonPath("$.summary.BLOCKED").value(0))
                .andExpect(jsonPath("$.summary.IN_MAINTENANCE").value(1))
                .andExpect(jsonPath("$.summary.INACTIVE_EXPIRED_DOCUMENTS").value(0))
                .andExpect(jsonPath("$.summary.DELETED").doesNotExist());
    }

    @Test
    void list_emptyFleet_returnsEmptyContentAndSummaryInZeros() throws Exception {
        mvc.perform(get("/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.summary.ACTIVE").value(0))
                .andExpect(jsonPath("$.summary.IN_MAINTENANCE").value(0));
    }

    @Test
    void list_filterByStatusAndType_returnsOnlyMatchesButSummaryCountsWholeFleet() throws Exception {
        createVehicle("LST011", "CAR");
        createVehicle("LST012", "PICKUP_TRUCK");
        forceStatus(createVehicle("LST013", "PICKUP_TRUCK"), "IN_MAINTENANCE");

        mvc.perform(get("/vehicles").param("status", "ACTIVE").param("type", "PICKUP_TRUCK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].plate").value("LST012"))
                .andExpect(jsonPath("$.summary.ACTIVE").value(2))
                .andExpect(jsonPath("$.summary.IN_MAINTENANCE").value(1));
    }

    @Test
    void list_paginated_splitsTheFleetWithoutRepeatingVehicles() throws Exception {
        createVehicle("LST021", "CAR");
        createVehicle("LST022", "CAR");
        createVehicle("LST023", "CAR");

        String firstPage = mvc.perform(get("/vehicles").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        String secondPage = mvc.perform(get("/vehicles").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andReturn().getResponse().getContentAsString();

        List<String> plates = new ArrayList<>(platesOf(firstPage));
        plates.addAll(platesOf(secondPage));
        assertThat(plates).containsExactlyInAnyOrder("LST021", "LST022", "LST023");
    }

    @Test
    void list_pageBeyondTheEnd_returns200WithEmptyContentAndRealTotal() throws Exception {
        createVehicle("LST031", "CAR");

        mvc.perform(get("/vehicles").param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(5))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content", empty()));
    }

    @Test
    void list_everyParameterInvalid_returns400ListingAllOfThem() throws Exception {
        mvc.perform(get("/vehicles").param("status", "active").param("type", "PLANE")
                        .param("page", "-1").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/vehicles"))
                .andExpect(jsonPath("$.timestamp", matchesPattern(".*-05:00$")))
                .andExpect(jsonPath("$.errors", hasSize(4)))
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors[1].field").value("type"))
                .andExpect(jsonPath("$.errors[1].code").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors[2].field").value("page"))
                .andExpect(jsonPath("$.errors[2].code").value("OUT_OF_RANGE"))
                .andExpect(jsonPath("$.errors[3].field").value("size"))
                .andExpect(jsonPath("$.errors[3].code").value("OUT_OF_RANGE"));
    }

    @Test
    void list_pageAndSizeThatAreNotNumbers_returns400InvalidFormat() throws Exception {
        mvc.perform(get("/vehicles").param("page", "abc").param("size", "1.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].field").value("page"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors[1].field").value("size"))
                .andExpect(jsonPath("$.errors[1].code").value("INVALID_FORMAT"));
    }

    @Test
    void list_statusDeleted_returns400AndDoesNotExposeDeletedVehicles() throws Exception {
        markDeleted(createVehicle("LST041", "CAR"));

        mvc.perform(get("/vehicles").param("status", "DELETED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.content").doesNotExist());
    }
}