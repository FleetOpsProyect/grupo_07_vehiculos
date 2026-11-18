package co.edu.univalle.fleetops.vehicles.infrastructure.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.univalle.fleetops.vehicles.application.port.in.ChangeToMaintenanceUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.GetVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.RegisterVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.controller.VehicleController;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VehicleController.class)
class VehicleDatabaseUnavailableTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RegisterVehicleUseCase registerVehicle;

    @MockitoBean
    private GetVehicleUseCase getVehicle;

    @MockitoBean
    private ChangeToMaintenanceUseCase changeToMaintenance;

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-17T22:00:00-05:00"), ZoneOffset.ofHours(-5));
        }
    }

    @Test
    void get_databaseDown_returns503WithoutStatusField() throws Exception {
        UUID id = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        String correlationId = "6f1c2b7e-3d4a-4b5c-9d6e-7f8a9b0c1d2e";

        when(getVehicle.execute(any()))
                .thenThrow(new DataAccessResourceFailureException("Connection refused"));

        mvc.perform(get("/vehicles/" + id).header("X-Correlation-Id", correlationId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("X-Correlation-Id", correlationId))
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Service temporarily unavailable, please try again"))
                .andExpect(jsonPath("$.path").value("/vehicles/" + id))
                .andExpect(jsonPath("$.timestamp", matchesPattern(".*-05:00$")))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }
}