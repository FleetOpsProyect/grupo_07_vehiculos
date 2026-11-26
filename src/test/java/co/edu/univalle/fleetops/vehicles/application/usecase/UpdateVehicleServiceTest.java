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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.univalle.fleetops.vehicles.application.command.UpdateVehicleCommand;
import co.edu.univalle.fleetops.vehicles.application.event.VehicleEvents;
import co.edu.univalle.fleetops.vehicles.application.port.out.EventOutbox;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleResult;
import co.edu.univalle.fleetops.vehicles.application.support.PassThroughTransactionPort;
import co.edu.univalle.fleetops.vehicles.domain.exception.ValidationException;
import co.edu.univalle.fleetops.vehicles.domain.exception.VehicleNotFoundException;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
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
class UpdateVehicleServiceTest {

    @Mock
    private VehicleRepository vehicles;
    @Mock
    private EventOutbox outbox;
    @Captor
    private ArgumentCaptor<Map<String, Object>> data;

    private UpdateVehicleService service;

    @BeforeEach
    void setUp() {
        service = new UpdateVehicleService(vehicles, outbox, new PassThroughTransactionPort(), CLOCK);
    }

    @Test
    void execute_validCommand_replacesEditableFields() {
        Vehicle vehicle = activeVehicle();
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));
        when(vehicles.save(any(Vehicle.class))).then(returnsFirstArg());

        VehicleResult result = service.execute(
                new UpdateVehicleCommand(vehicle.id(), "Toyota", "Hilux SR", 2023, "Silver", null));

        assertThat(result.model()).isEqualTo("Hilux SR");
        assertThat(result.color()).isEqualTo("Silver");
        assertThat(result.notes()).isNull();
        assertThat(result.plate()).isEqualTo("ABC123");
        assertThat(result.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void execute_unknownVehicle_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(vehicles.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(new UpdateVehicleCommand(id, "Toyota", "Hilux", 2023, null, null)))
                .isInstanceOf(VehicleNotFoundException.class);
    }

    @Test
    void execute_deletedVehicle_throwsNotFound() {
        Vehicle vehicle = activeVehicle();
        vehicle.markDeleted(NOW);
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> service.execute(
                new UpdateVehicleCommand(vehicle.id(), "Toyota", "Hilux", 2023, null, null)))
                .isInstanceOf(VehicleNotFoundException.class);
    }

    @Test
    void execute_invalidFields_throwsValidationWithoutSaving() {
        Vehicle vehicle = activeVehicle();
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> service.execute(
                new UpdateVehicleCommand(vehicle.id(), null, "Hilux", 2023, null, null)))
                .isInstanceOf(ValidationException.class);

        verify(vehicles, never()).save(any());
    }

    @Test
    void execute_changedFields_recordsVehicleUpdatedEventWithTheChangedFieldsAndNextVersion() {
        Vehicle vehicle = activeVehicle();
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));
        when(vehicles.save(any(Vehicle.class))).then(returnsFirstArg());

        service.execute(new UpdateVehicleCommand(vehicle.id(), "Toyota", "Hilux SR", 2023, "Silver", null));

        verify(outbox).record(eq(VehicleEvents.UPDATED), eq(vehicle.id()), data.capture());
        assertThat(data.getValue())
                .containsEntry("changed_fields", List.of("model", "color", "notes"))
                .containsEntry("model", "Hilux SR")
                .containsEntry("version_number", 1L);
    }

    @Test
    void execute_sameData_returnsTheVehicleWithoutSavingOrRecordingEvents() {
        Vehicle vehicle = activeVehicle();
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));

        VehicleResult result = service.execute(new UpdateVehicleCommand(vehicle.id(), "Toyota", "Hilux", 2023,
                "White", "Assigned to Cali branch"));

        assertThat(result.updatedAt()).isEqualTo(vehicle.createdAt());
        verify(vehicles, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void execute_invalidFields_doesNotRecordEvents() {
        Vehicle vehicle = activeVehicle();
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> service.execute(new UpdateVehicleCommand(vehicle.id(), "", "Hilux", 2023, null, null)))
                .isInstanceOf(ValidationException.class);

        verifyNoInteractions(outbox);
    }
}
