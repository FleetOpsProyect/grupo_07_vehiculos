package co.edu.univalle.fleetops.vehicles.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.univalle.fleetops.vehicles.application.command.ChangeToMaintenanceCommand;
import co.edu.univalle.fleetops.vehicles.application.exception.ConcurrentVehicleUpdateException;
import co.edu.univalle.fleetops.vehicles.application.port.in.ChangeToMaintenanceUseCase;
import co.edu.univalle.fleetops.vehicles.infrastructure.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Comprueba, con el contexto completo, que un conflicto de concurrencia llega como 503 y no como 500. */
@AutoConfigureMockMvc
class ConcurrentUpdateResponseIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private ChangeToMaintenanceUseCase changeToMaintenance;

    @Test
    void put_concurrentUpdateThatSurvivesTheRetry_returns503ServiceUnavailable() throws Exception {
        when(changeToMaintenance.execute(any(ChangeToMaintenanceCommand.class)))
                .thenThrow(new ConcurrentVehicleUpdateException(new RuntimeException("stale version")));

        mvc.perform(put("/vehicles/3fa85f64-5717-4562-b3fc-2c963f66afa6/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"IN_MAINTENANCE\", "
                                + "\"maintenance_id\": \"c56a4180-65aa-42ec-a945-5fd21dec0538\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }
}
