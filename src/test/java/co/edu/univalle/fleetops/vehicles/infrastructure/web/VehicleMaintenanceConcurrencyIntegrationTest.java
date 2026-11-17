package co.edu.univalle.fleetops.vehicles.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import co.edu.univalle.fleetops.vehicles.infrastructure.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class VehicleMaintenanceConcurrencyIntegrationTest extends PostgresIntegrationTest {

    private static final int ROUNDS = 25;
    private static final String VEHICLE_BODY = """
            {"plate": "%s", "vehicle_type": "CAR", "brand": "Kia", "model": "Rio", "year": 2021,
             "odometer_km": 50}""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private String createVehicle(String plate) throws Exception {
        String response = mvc.perform(post("/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content(VEHICLE_BODY.formatted(plate)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("vehicle_id").asText();
    }

    private List<Integer> putConcurrently(String vehicleId, String... bodies) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(bodies.length);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> responses = new ArrayList<>();
        try {
            for (String body : bodies) {
                Callable<Integer> request = () -> {
                    start.await();
                    return mvc.perform(put("/vehicles/" + vehicleId + "/status")
                                    .contentType(MediaType.APPLICATION_JSON).content(body))
                            .andReturn().getResponse().getStatus();
                };
                responses.add(pool.submit(request));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> response : responses) {
                statuses.add(response.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private int count(String sql, String vehicleId) {
        return jdbc.queryForObject(sql, Integer.class, vehicleId);
    }

    @Test
    void put_sameRequestConcurrently_bothReturn200AndOnlyOneBlockAndHistoryRowRemain() throws Exception {
        String body = "{\"status\": \"IN_MAINTENANCE\", \"maintenance_id\": \"%s\"}";
        for (int round = 0; round < ROUNDS; round++) {
            String id = createVehicle("CNC%03d".formatted(round));
            String sameRequest = body.formatted(UUID.randomUUID());

            List<Integer> statuses = putConcurrently(id, sameRequest, sameRequest);

            assertThat(statuses).as("round %d", round).containsExactly(200, 200);
            assertThat(count("SELECT count(*) FROM vehicle_block WHERE vehicle_id = ?::uuid", id)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM status_history WHERE vehicle_id = ?::uuid "
                    + "AND cause = 'MAINTENANCE_STARTED'", id)).isEqualTo(1);
        }
    }

    @Test
    void put_differentMaintenanceIdsConcurrently_oneWinsAndTheOtherGets409() throws Exception {
        String body = "{\"status\": \"IN_MAINTENANCE\", \"maintenance_id\": \"%s\"}";
        for (int round = 0; round < ROUNDS; round++) {
            String id = createVehicle("CND%03d".formatted(round));

            List<Integer> statuses = putConcurrently(id, body.formatted(UUID.randomUUID()),
                    body.formatted(UUID.randomUUID()));

            assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(200, 409);
            assertThat(count("SELECT count(*) FROM vehicle_block WHERE vehicle_id = ?::uuid", id)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM status_history WHERE vehicle_id = ?::uuid "
                    + "AND cause = 'MAINTENANCE_STARTED'", id)).isEqualTo(1);
        }
    }
}
