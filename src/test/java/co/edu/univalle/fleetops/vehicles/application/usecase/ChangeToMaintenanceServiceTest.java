package co.edu.univalle.fleetops.vehicles.application.usecase;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.CLOCK;
import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.NOW;
import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.activeVehicle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.univalle.fleetops.vehicles.application.command.ChangeToMaintenanceCommand;
import co.edu.univalle.fleetops.vehicles.application.event.VehicleEvents;
import co.edu.univalle.fleetops.vehicles.application.exception.ConcurrentVehicleUpdateException;
import co.edu.univalle.fleetops.vehicles.application.port.out.StatusHistoryRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.EventOutbox;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleResult;
import co.edu.univalle.fleetops.vehicles.application.support.PassThroughTransactionPort;
import co.edu.univalle.fleetops.vehicles.domain.exception.InvalidStatusTransitionException;
import co.edu.univalle.fleetops.vehicles.domain.exception.StatusNotAllowedException;
import co.edu.univalle.fleetops.vehicles.domain.exception.VehicleNotFoundException;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusChangeCause;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusHistoryEntry;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChangeToMaintenanceServiceTest {

    @Mock
    private VehicleRepository vehicles;
    @Mock
    private StatusHistoryRepository history;
    @Mock
    private EventOutbox outbox;
    @Captor
    private ArgumentCaptor<Map<String, Object>> data;

    private ChangeToMaintenanceService service;

    @BeforeEach
    void setUp() {
        service = new ChangeToMaintenanceService(vehicles, history, outbox, new PassThroughTransactionPort(), CLOCK);
    }

    @Test
    void execute_activeVehicle_movesToMaintenanceAndRecordsHistory() {
        Vehicle vehicle = activeVehicle();
        UUID maintenanceId = UUID.randomUUID();
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));
        when(vehicles.save(any(Vehicle.class))).then(returnsFirstArg());

        VehicleResult result = service.execute(
                new ChangeToMaintenanceCommand(vehicle.id(), VehicleStatus.IN_MAINTENANCE, maintenanceId));

        assertThat(result.status()).isEqualTo(VehicleStatus.IN_MAINTENANCE);
        assertThat(result.activeBlocks()).singleElement()
                .satisfies(block -> assertThat(block.blockKey()).isEqualTo("MAINTENANCE:" + maintenanceId));
        ArgumentCaptor<StatusHistoryEntry> entry = ArgumentCaptor.forClass(StatusHistoryEntry.class);
        verify(history).save(entry.capture());
        assertThat(entry.getValue().fromStatus()).isEqualTo(VehicleStatus.ACTIVE);
        assertThat(entry.getValue().toStatus()).isEqualTo(VehicleStatus.IN_MAINTENANCE);
        assertThat(entry.getValue().cause()).isEqualTo(StatusChangeCause.MAINTENANCE_STARTED);
        assertThat(entry.getValue().sourceId()).isEqualTo(maintenanceId.toString());
        assertThat(entry.getValue().changedAt()).isEqualTo(NOW);
    }

    @Test
    void execute_sameMaintenanceIdTwice_secondCallChangesNothing() {
        Vehicle vehicle = activeVehicle();
        UUID maintenanceId = UUID.randomUUID();
        vehicle.startMaintenance(maintenanceId, NOW);
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));

        VehicleResult result = service.execute(
                new ChangeToMaintenanceCommand(vehicle.id(), VehicleStatus.IN_MAINTENANCE, maintenanceId));

        assertThat(result.status()).isEqualTo(VehicleStatus.IN_MAINTENANCE);
        verify(vehicles, never()).save(any());
        verifyNoInteractions(history, outbox);
    }

    @Test
    void execute_otherStatusRequested_throwsStatusNotAllowed() {
        Vehicle vehicle = activeVehicle();
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));
        var command = new ChangeToMaintenanceCommand(vehicle.id(), VehicleStatus.BLOCKED, UUID.randomUUID());

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(StatusNotAllowedException.class);

        verify(vehicles, never()).save(any());
    }

    @Test
    void execute_vehicleNotActive_throwsInvalidTransition() {
        Vehicle vehicle = activeVehicle();
        vehicle.startMaintenance(UUID.randomUUID(), NOW);
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));
        var command = new ChangeToMaintenanceCommand(vehicle.id(), VehicleStatus.IN_MAINTENANCE, UUID.randomUUID());

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(InvalidStatusTransitionException.class);

        verifyNoInteractions(history);
    }

    @Test
    void execute_unknownVehicle_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(vehicles.findById(id)).thenReturn(Optional.empty());
        var command = new ChangeToMaintenanceCommand(id, VehicleStatus.IN_MAINTENANCE, UUID.randomUUID());

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(VehicleNotFoundException.class);
    }

    @Test
    void execute_unknownVehicleAndOtherStatus_throwsNotFoundBeforeStatusNotAllowed() {
        UUID id = UUID.randomUUID();
        when(vehicles.findById(id)).thenReturn(Optional.empty());
        var command = new ChangeToMaintenanceCommand(id, VehicleStatus.ACTIVE, UUID.randomUUID());

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(VehicleNotFoundException.class);
    }

    @Test
    void execute_deletedVehicle_throwsNotFound() {
        Vehicle vehicle = activeVehicle();
        vehicle.markDeleted(NOW);
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));
        var command = new ChangeToMaintenanceCommand(vehicle.id(), VehicleStatus.IN_MAINTENANCE, UUID.randomUUID());

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(VehicleNotFoundException.class);

        verify(vehicles, never()).save(any());
    }

    private static Vehicle reloadedCopyOf(Vehicle vehicle) {
        return Vehicle.restore(vehicle.id(), vehicle.plate(), vehicle.type(), vehicle.details(),
                vehicle.odometerKm(), vehicle.status(), vehicle.blocks(), vehicle.activeRouteId(),
                vehicle.isDeleted(), vehicle.versionNumber(), vehicle.createdAt(), vehicle.updatedAt());
    }

    @Test
    void execute_conflictOnSaveAndReloadShowsSameMaintenance_returnsIdempotentResult() {
        Vehicle firstRead = activeVehicle();
        Vehicle reloaded = reloadedCopyOf(firstRead);
        UUID maintenanceId = UUID.randomUUID();
        reloaded.startMaintenance(maintenanceId, NOW);
        when(vehicles.findById(firstRead.id())).thenReturn(Optional.of(firstRead), Optional.of(reloaded));
        when(vehicles.save(any(Vehicle.class))).thenThrow(new ConcurrentVehicleUpdateException(new RuntimeException()));

        VehicleResult result = service.execute(
                new ChangeToMaintenanceCommand(firstRead.id(), VehicleStatus.IN_MAINTENANCE, maintenanceId));

        assertThat(result.status()).isEqualTo(VehicleStatus.IN_MAINTENANCE);
        assertThat(result.activeBlocks()).singleElement()
                .satisfies(block -> assertThat(block.blockKey()).isEqualTo("MAINTENANCE:" + maintenanceId));
        verify(vehicles, times(2)).findById(firstRead.id());
        verify(vehicles, times(1)).save(any(Vehicle.class));
        verifyNoInteractions(history);
    }

    @Test
    void execute_conflictOnSaveAndReloadShowsOtherMaintenance_throwsInvalidTransition() {
        Vehicle firstRead = activeVehicle();
        Vehicle reloaded = reloadedCopyOf(firstRead);
        reloaded.startMaintenance(UUID.randomUUID(), NOW);
        when(vehicles.findById(firstRead.id())).thenReturn(Optional.of(firstRead), Optional.of(reloaded));
        when(vehicles.save(any(Vehicle.class))).thenThrow(new ConcurrentVehicleUpdateException(new RuntimeException()));
        var command = new ChangeToMaintenanceCommand(firstRead.id(), VehicleStatus.IN_MAINTENANCE, UUID.randomUUID());

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(InvalidStatusTransitionException.class);

        verify(vehicles, times(1)).save(any(Vehicle.class));
        verifyNoInteractions(history);
    }

    @Test
    void execute_conflictOnSaveAndReloadStillActive_appliesTheChangeOnTheRetry() {
        Vehicle firstRead = activeVehicle();
        Vehicle reloaded = reloadedCopyOf(firstRead);
        UUID maintenanceId = UUID.randomUUID();
        when(vehicles.findById(firstRead.id())).thenReturn(Optional.of(firstRead), Optional.of(reloaded));
        when(vehicles.save(any(Vehicle.class)))
                .thenThrow(new ConcurrentVehicleUpdateException(new RuntimeException()))
                .then(returnsFirstArg());

        VehicleResult result = service.execute(
                new ChangeToMaintenanceCommand(firstRead.id(), VehicleStatus.IN_MAINTENANCE, maintenanceId));

        assertThat(result.status()).isEqualTo(VehicleStatus.IN_MAINTENANCE);
        verify(vehicles, times(2)).save(any(Vehicle.class));
        ArgumentCaptor<StatusHistoryEntry> entry = ArgumentCaptor.forClass(StatusHistoryEntry.class);
        verify(history, times(1)).save(entry.capture());
        assertThat(entry.getValue().sourceId()).isEqualTo(maintenanceId.toString());
    }

    @Test
    void execute_conflictOnTheRetryToo_propagatesAfterOnlyOneRetry() {
        Vehicle firstRead = activeVehicle();
        Vehicle reloaded = reloadedCopyOf(firstRead);
        when(vehicles.findById(firstRead.id())).thenReturn(Optional.of(firstRead), Optional.of(reloaded));
        when(vehicles.save(any(Vehicle.class))).thenThrow(new ConcurrentVehicleUpdateException(new RuntimeException()));
        var command = new ChangeToMaintenanceCommand(firstRead.id(), VehicleStatus.IN_MAINTENANCE, UUID.randomUUID());

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(ConcurrentVehicleUpdateException.class);

        verify(vehicles, times(2)).findById(firstRead.id());
        verify(vehicles, times(2)).save(any(Vehicle.class));
        verifyNoInteractions(history);
    }

    @Test
    void execute_activeVehicle_recordsStatusChangedEventWithTheMaintenanceCause() {
        Vehicle vehicle = activeVehicle();
        UUID maintenanceId = UUID.randomUUID();
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));
        when(vehicles.save(any(Vehicle.class))).then(returnsFirstArg());

        service.execute(new ChangeToMaintenanceCommand(vehicle.id(), VehicleStatus.IN_MAINTENANCE, maintenanceId));

        verify(outbox).record(eq(VehicleEvents.STATUS_CHANGED), eq(vehicle.id()), data.capture());
        assertThat(data.getValue())
                .containsEntry("previous_status", "ACTIVE")
                .containsEntry("status", "IN_MAINTENANCE")
                .containsEntry("cause", Map.of("type", "MAINTENANCE", "action", "BLOCK_ADDED",
                        "source_id", maintenanceId.toString()))
                .containsEntry("active_blocks", List.of(Map.of("block_key", "MAINTENANCE:" + maintenanceId,
                        "type", "MAINTENANCE")))
                .containsEntry("is_assignable", false)
                .containsEntry("version_number", 1L);
    }
}
