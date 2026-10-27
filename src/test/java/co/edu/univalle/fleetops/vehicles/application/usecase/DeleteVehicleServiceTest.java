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

import co.edu.univalle.fleetops.vehicles.application.port.out.ActiveAssignmentsPort;
import co.edu.univalle.fleetops.vehicles.application.port.out.StatusHistoryRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.result.DeletedVehicleResult;
import co.edu.univalle.fleetops.vehicles.application.support.PassThroughTransactionPort;
import co.edu.univalle.fleetops.vehicles.application.exception.AssignmentServiceUnavailableException;
import co.edu.univalle.fleetops.vehicles.domain.exception.VehicleHasActiveAssignmentsException;
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
class DeleteVehicleServiceTest {

    @Mock
    private VehicleRepository vehicles;
    @Mock
    private StatusHistoryRepository history;
    @Mock
    private ActiveAssignmentsPort assignments;

    private DeleteVehicleService service;

    @BeforeEach
    void setUp() {
        service = new DeleteVehicleService(vehicles, history, assignments, new PassThroughTransactionPort(),
                CLOCK);
    }

    @Test
    void execute_noActiveAssignments_marksDeletedAndRecordsHistory() {
        Vehicle vehicle = activeVehicle();
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));
        when(assignments.countActiveAssignments(vehicle.id())).thenReturn(0);
        when(vehicles.save(any(Vehicle.class))).then(returnsFirstArg());

        DeletedVehicleResult result = service.execute(vehicle.id());

        assertThat(result.status()).isEqualTo(VehicleStatus.DELETED);
        assertThat(result.deleted()).isTrue();
        assertThat(result.updatedAt()).isEqualTo(NOW);
        ArgumentCaptor<StatusHistoryEntry> entry = ArgumentCaptor.forClass(StatusHistoryEntry.class);
        verify(history).save(entry.capture());
        assertThat(entry.getValue().fromStatus()).isEqualTo(VehicleStatus.ACTIVE);
        assertThat(entry.getValue().toStatus()).isEqualTo(VehicleStatus.DELETED);
        assertThat(entry.getValue().cause()).isEqualTo(StatusChangeCause.VEHICLE_DELETED);
    }

    @Test
    void execute_activeAssignments_throwsConflictWithoutDeleting() {
        Vehicle vehicle = activeVehicle();
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));
        when(assignments.countActiveAssignments(vehicle.id())).thenReturn(2);

        assertThatThrownBy(() -> service.execute(vehicle.id()))
                .isInstanceOf(VehicleHasActiveAssignmentsException.class)
                .hasMessageContaining("2");

        verify(vehicles, never()).save(any());
        verifyNoInteractions(history);
    }

    @Test
    void execute_assignmentsServiceDown_throwsAndDoesNotDelete() {
        Vehicle vehicle = activeVehicle();
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));
        when(assignments.countActiveAssignments(vehicle.id()))
                .thenThrow(new AssignmentServiceUnavailableException(new RuntimeException("timeout")));

        assertThatThrownBy(() -> service.execute(vehicle.id()))
                .isInstanceOf(AssignmentServiceUnavailableException.class);

        verify(vehicles, never()).save(any());
    }

    @Test
    void execute_alreadyDeleted_returnsOkWithoutConsultingAssignments() {
        Vehicle vehicle = activeVehicle();
        vehicle.markDeleted(NOW);
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));

        DeletedVehicleResult result = service.execute(vehicle.id());

        assertThat(result.deleted()).isTrue();
        verifyNoInteractions(assignments, history);
    }

    @Test
    void execute_unknownVehicle_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(vehicles.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(id)).isInstanceOf(VehicleNotFoundException.class);
    }
}
