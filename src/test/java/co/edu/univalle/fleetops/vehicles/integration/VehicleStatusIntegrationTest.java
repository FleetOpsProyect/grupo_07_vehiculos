package co.edu.univalle.fleetops.vehicles.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.univalle.fleetops.vehicles.dto.CreateVehicleRequest;
import co.edu.univalle.fleetops.vehicles.dto.VehicleResponse;
import co.edu.univalle.fleetops.vehicles.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.model.VehicleType;
import co.edu.univalle.fleetops.vehicles.repository.AppliedRouteRepository;
import co.edu.univalle.fleetops.vehicles.repository.StatusHistoryRepository;
import co.edu.univalle.fleetops.vehicles.repository.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.service.VehicleService;
import co.edu.univalle.fleetops.vehicles.service.VehicleStatusService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Lógica de eventos contra PostgreSQL real: bloqueos, historial y odómetro persistidos
@DisplayName("Tests de integración - Estados del vehículo")
class VehicleStatusIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private VehicleStatusService statusService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private StatusHistoryRepository historyRepository;

    @Autowired
    private AppliedRouteRepository appliedRouteRepository;

    private UUID vehicleId;

    @BeforeEach
    void prepararDatos() {
        historyRepository.deleteAll();
        appliedRouteRepository.deleteAll();
        vehicleRepository.deleteAll();
        vehicleId = vehicleService.create(new CreateVehicleRequest("ABC123", VehicleType.PICKUP_TRUCK,
                "Toyota", "Hilux", 2023, null, null, new BigDecimal("15200"))).vehicleId();
    }

    @Test
    @DisplayName("Incidente + SOAT vencido: solo vuelve a ACTIVE cuando se quitan ambos bloqueos")
    void whenIncidentAndExpiredDocument_thenActiveOnlyAfterBothRemoved() {
        UUID incidentId = UUID.randomUUID();

        statusService.applyIncident(vehicleId, incidentId, "MECHANICAL_FAILURE", true);
        statusService.markDocumentExpired(vehicleId, UUID.randomUUID(), "SOAT");
        assertEquals(VehicleStatus.BLOCKED, vehicleService.getById(vehicleId).status());

        statusService.finishMaintenance(vehicleId, UUID.randomUUID(), incidentId);
        assertEquals(VehicleStatus.INACTIVE_EXPIRED_DOCUMENTS, vehicleService.getById(vehicleId).status());

        VehicleResponse result = statusService.markDocumentRenewed(vehicleId, UUID.randomUUID(), "SOAT");
        assertEquals(VehicleStatus.ACTIVE, result.status());
        assertTrue(result.activeBlocks().isEmpty());

        // Creación, BLOCKED, INACTIVE_EXPIRED_DOCUMENTS y ACTIVE
        assertEquals(4, historyRepository.findAllByVehicleIdOrderByChangedAtDesc(vehicleId).size());
    }

    @Test
    @DisplayName("La misma ruta finalizada dos veces suma el kilometraje una sola vez")
    void whenRouteFinishedTwice_thenOdometerAddedOnce() {
        UUID routeId = UUID.randomUUID();
        statusService.startRoute(vehicleId, routeId);
        assertEquals(VehicleStatus.ON_ROUTE, vehicleService.getById(vehicleId).status());

        statusService.finishRoute(vehicleId, routeId, new BigDecimal("15.5"));
        VehicleResponse result = statusService.finishRoute(vehicleId, routeId, new BigDecimal("15.5"));

        assertEquals(0, new BigDecimal("15215.5").compareTo(result.odometerKm()));
        assertEquals(VehicleStatus.ACTIVE, result.status());
        assertTrue(appliedRouteRepository.existsById(routeId));
    }
}
