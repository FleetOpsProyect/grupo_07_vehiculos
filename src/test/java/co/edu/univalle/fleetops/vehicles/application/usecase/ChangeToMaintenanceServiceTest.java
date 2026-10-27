package co.edu.univalle.fleetops.vehicles.application.usecase;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.CLOCK;
import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.NOW;
import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.activeVehicle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.univalle.fleetops.vehicles.application.command.ChangeToMaintenanceCommand;
import co.edu.univalle.fleetops.vehicles.application.port.out.StatusHistoryRepository;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChangeToMaintenanceServiceTest {

    @Mock
    private VehicleRepository vehicles;
    @Mock
    private StatusHistoryRepository history;

    private ChangeToMaintenanceService service;

    @BeforeEach
    void setUp() {
        service = new ChangeToMaintenanceService(vehicles, history, new PassThroughTransactionPort(), CLOCK);
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
        verifyNoInteractions(history);
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
    void execute_deletedVehicle_throwsNotFound() {
        Vehicle vehicle = activeVehicle();
        vehicle.markDeleted(NOW);
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));
        var command = new ChangeToMaintenanceCommand(vehicle.id(), VehicleStatus.IN_MAINTENANCE, UUID.randomUUID());

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(VehicleNotFoundException.class);

        verify(vehicles, never()).save(any());
    }
}
