package co.edu.univalle.fleetops.vehicles.service;

import static co.edu.univalle.fleetops.vehicles.service.VehicleFixtures.CLOCK;
import static co.edu.univalle.fleetops.vehicles.service.VehicleFixtures.VEHICLE_ID;
import static co.edu.univalle.fleetops.vehicles.service.VehicleFixtures.activeVehicle;
import static co.edu.univalle.fleetops.vehicles.service.VehicleFixtures.withBlock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import co.edu.univalle.fleetops.vehicles.dto.VehicleResponse;
import co.edu.univalle.fleetops.vehicles.exception.ResourceNotFoundException;
import co.edu.univalle.fleetops.vehicles.exception.ValidationException;
import co.edu.univalle.fleetops.vehicles.model.AppliedRouteModel;
import co.edu.univalle.fleetops.vehicles.model.BlockType;
import co.edu.univalle.fleetops.vehicles.model.StatusHistoryModel;
import co.edu.univalle.fleetops.vehicles.model.VehicleModel;
import co.edu.univalle.fleetops.vehicles.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.repository.AppliedRouteRepository;
import co.edu.univalle.fleetops.vehicles.repository.StatusHistoryRepository;
import co.edu.univalle.fleetops.vehicles.repository.VehicleRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

// Lógica de bloqueos (ADR-09): cada evento agrega o quita su bloqueo y el estado se recalcula
@ExtendWith(SpringExtension.class)
class VehicleStatusServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private AppliedRouteRepository appliedRouteRepository;

    @Mock
    private StatusHistoryRepository historyRepository;

    private VehicleStatusService statusService;

    private VehicleModel vehicle;

    @BeforeEach
    void setUp() {
        statusService = new VehicleStatusService(vehicleRepository, appliedRouteRepository,
                new StatusHistoryRecorder(historyRepository, CLOCK), CLOCK);
        vehicle = activeVehicle();
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(vehicle));
        given(vehicleRepository.save(any(VehicleModel.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    // ── Incidentes ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Incidente que exige bloqueo deja el vehículo BLOCKED y guarda historial")
    void whenApplyIncident_requiringBlock_thenBlocked() {
        UUID incidentId = UUID.randomUUID();

        VehicleResponse result = statusService.applyIncident(VEHICLE_ID, incidentId, "MECHANICAL_FAILURE", true);

        assertEquals(VehicleStatus.BLOCKED, result.status());
        assertEquals("INCIDENT:" + incidentId, result.activeBlocks().get(0).blockKey());
        verify(historyRepository).save(any(StatusHistoryModel.class));
    }

    @Test
    @DisplayName("Incidente leve que no exige bloqueo no cambia el vehículo")
    void whenApplyIncident_notRequiringBlock_thenNoChanges() {
        VehicleResponse result = statusService.applyIncident(VEHICLE_ID, UUID.randomUUID(), "MINOR", false);

        assertEquals(VehicleStatus.ACTIVE, result.status());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("El mismo incidente recibido dos veces no crea dos bloqueos")
    void whenApplyIncident_twice_thenSingleBlock() {
        UUID incidentId = UUID.randomUUID();
        withBlock(vehicle, BlockType.INCIDENT, incidentId.toString());

        VehicleResponse result = statusService.applyIncident(VEHICLE_ID, incidentId, "MECHANICAL_FAILURE", true);

        assertEquals(1, result.activeBlocks().size());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un vehículo eliminado ignora los incidentes")
    void whenApplyIncident_onDeletedVehicle_thenIgnored() {
        vehicle.setDeleted(true);
        vehicle.setStatus(VehicleStatus.DELETED);

        VehicleResponse result = statusService.applyIncident(VEHICLE_ID, UUID.randomUUID(), "CRASH", true);

        assertEquals(VehicleStatus.DELETED, result.status());
        verify(vehicleRepository, never()).save(any());
    }

    // ── Mantenimiento ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Fin del correctivo quita el bloqueo del incidente y el vehículo vuelve a ACTIVE")
    void whenFinishMaintenance_corrective_thenActive() {
        UUID incidentId = UUID.randomUUID();
        withBlock(vehicle, BlockType.INCIDENT, incidentId.toString());

        VehicleResponse result = statusService.finishMaintenance(VEHICLE_ID, UUID.randomUUID(), incidentId);

        assertEquals(VehicleStatus.ACTIVE, result.status());
        assertTrue(result.activeBlocks().isEmpty());
    }

    @Test
    @DisplayName("Fin del preventivo quita el bloqueo de la orden")
    void whenFinishMaintenance_preventive_thenActive() {
        UUID maintenanceId = UUID.randomUUID();
        withBlock(vehicle, BlockType.MAINTENANCE, maintenanceId.toString());

        VehicleResponse result = statusService.finishMaintenance(VEHICLE_ID, maintenanceId, null);

        assertEquals(VehicleStatus.ACTIVE, result.status());
    }

    @Test
    @DisplayName("Fin de una orden que no bloqueó el vehículo no cambia nada")
    void whenFinishMaintenance_withUnknownOrder_thenNoChanges() {
        statusService.finishMaintenance(VEHICLE_ID, UUID.randomUUID(), null);

        verify(vehicleRepository, never()).save(any());
    }

    // ── Documentos ────────────────────────────────────────────────────────

    @Test
    @DisplayName("SOAT vencido deja el vehículo INACTIVE_EXPIRED_DOCUMENTS")
    void whenDocumentExpired_thenInactiveExpiredDocuments() {
        VehicleResponse result = statusService.markDocumentExpired(VEHICLE_ID, UUID.randomUUID(), "SOAT");

        assertEquals(VehicleStatus.INACTIVE_EXPIRED_DOCUMENTS, result.status());
        assertEquals("DOCUMENT:SOAT", result.activeBlocks().get(0).blockKey());
    }

    @Test
    @DisplayName("El mismo documento vencido dos veces no crea dos bloqueos")
    void whenDocumentExpired_twice_thenNoChanges() {
        withBlock(vehicle, BlockType.DOCUMENT, "SOAT");

        statusService.markDocumentExpired(VEHICLE_ID, UUID.randomUUID(), "SOAT");

        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Renovar el SOAT vuelve el vehículo a ACTIVE")
    void whenDocumentRenewed_thenActive() {
        withBlock(vehicle, BlockType.DOCUMENT, "SOAT");

        VehicleResponse result = statusService.markDocumentRenewed(VEHICLE_ID, UUID.randomUUID(), "SOAT");

        assertEquals(VehicleStatus.ACTIVE, result.status());
    }

    @Test
    @DisplayName("Renovar un documento que no estaba vencido no cambia nada")
    void whenDocumentRenewed_withoutBlock_thenNoChanges() {
        statusService.markDocumentRenewed(VEHICLE_ID, UUID.randomUUID(), "SOAT");

        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Con incidente y SOAT vencido, renovar el SOAT deja el vehículo BLOCKED")
    void whenIncidentAndDocument_andRenewDocument_thenStillBlocked() {
        UUID incidentId = UUID.randomUUID();
        withBlock(vehicle, BlockType.INCIDENT, incidentId.toString());
        withBlock(vehicle, BlockType.DOCUMENT, "SOAT");

        VehicleResponse renewed = statusService.markDocumentRenewed(VEHICLE_ID, UUID.randomUUID(), "SOAT");
        assertEquals(VehicleStatus.BLOCKED, renewed.status());

        VehicleResponse repaired = statusService.finishMaintenance(VEHICLE_ID, UUID.randomUUID(), incidentId);
        assertEquals(VehicleStatus.ACTIVE, repaired.status());
    }

    // ── Rutas ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Iniciar ruta deja el vehículo ON_ROUTE")
    void whenStartRoute_thenOnRoute() {
        UUID routeId = UUID.randomUUID();

        VehicleResponse result = statusService.startRoute(VEHICLE_ID, routeId);

        assertEquals(VehicleStatus.ON_ROUTE, result.status());
        assertEquals(routeId, result.activeRouteId());
    }

    @Test
    @DisplayName("El mismo inicio de ruta recibido dos veces no cambia nada")
    void whenStartRoute_twice_thenNoChanges() {
        UUID routeId = UUID.randomUUID();
        vehicle.setActiveRouteId(routeId);

        statusService.startRoute(VEHICLE_ID, routeId);

        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Finalizar ruta suma la distancia, libera la ruta y vuelve a ACTIVE")
    void whenFinishRoute_thenAddDistanceAndActive() {
        UUID routeId = UUID.randomUUID();
        vehicle.setActiveRouteId(routeId);
        vehicle.setStatus(VehicleStatus.ON_ROUTE);
        given(appliedRouteRepository.existsById(routeId)).willReturn(false);

        VehicleResponse result = statusService.finishRoute(VEHICLE_ID, routeId, new BigDecimal("15.5"));

        assertEquals(0, new BigDecimal("15215.5").compareTo(result.odometerKm()));
        assertNull(result.activeRouteId());
        assertEquals(VehicleStatus.ACTIVE, result.status());
        verify(appliedRouteRepository).save(any(AppliedRouteModel.class));
    }

    @Test
    @DisplayName("Una ruta ya aplicada no vuelve a sumar kilómetros (idempotencia)")
    void whenFinishRoute_alreadyApplied_thenOdometerUnchanged() {
        UUID routeId = UUID.randomUUID();
        given(appliedRouteRepository.existsById(routeId)).willReturn(true);

        VehicleResponse result = statusService.finishRoute(VEHICLE_ID, routeId, new BigDecimal("15.5"));

        assertEquals(0, new BigDecimal("15200.0").compareTo(result.odometerKm()));
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Finalizar ruta con distancia negativa lanza ValidationException")
    void whenFinishRoute_withNegativeDistance_thenThrowValidationException() {
        UUID routeId = UUID.randomUUID();
        BigDecimal distance = new BigDecimal("-1");

        assertThrows(ValidationException.class,
                () -> statusService.finishRoute(VEHICLE_ID, routeId, distance));
    }

    @Test
    @DisplayName("Un evento para un vehículo inexistente lanza ResourceNotFoundException")
    void whenEvent_forUnknownVehicle_thenThrowNotFound() {
        UUID unknown = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        given(vehicleRepository.findById(unknown)).willReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> statusService.startRoute(unknown, routeId));
    }
}
