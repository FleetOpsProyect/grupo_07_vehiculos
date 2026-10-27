package co.edu.univalle.fleetops.vehicles.application.usecase;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.CLOCK;
import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

import co.edu.univalle.fleetops.vehicles.application.command.RegisterVehicleCommand;
import co.edu.univalle.fleetops.vehicles.application.port.out.StatusHistoryRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleResult;
import co.edu.univalle.fleetops.vehicles.application.support.PassThroughTransactionPort;
import co.edu.univalle.fleetops.vehicles.domain.exception.PlateAlreadyExistsException;
import co.edu.univalle.fleetops.vehicles.domain.exception.ValidationException;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusChangeCause;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusHistoryEntry;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterVehicleServiceTest {

    @Mock
    private VehicleRepository vehicles;
    @Mock
    private StatusHistoryRepository history;

    private RegisterVehicleService service;

    @BeforeEach
    void setUp() {
        service = new RegisterVehicleService(vehicles, history, new PassThroughTransactionPort(), CLOCK);
    }

    private static RegisterVehicleCommand command(String plate) {
        return new RegisterVehicleCommand(plate, VehicleType.PICKUP_TRUCK, "Toyota", "Hilux", 2023, "White",
                "notes", new BigDecimal("15200"));
    }

    @Test
    void execute_validCommand_savesNormalizedActiveVehicleAndHistory() {
        when(vehicles.existsActiveByPlate("ABC123")).thenReturn(false);
        when(vehicles.save(any(Vehicle.class))).then(returnsFirstArg());

        VehicleResult result = service.execute(command("abc-123"));

        assertThat(result.plate()).isEqualTo("ABC123");
        assertThat(result.status()).isEqualTo(VehicleStatus.ACTIVE);
        assertThat(result.createdAt()).isEqualTo(NOW);
        ArgumentCaptor<StatusHistoryEntry> entry = ArgumentCaptor.forClass(StatusHistoryEntry.class);
        verify(history).save(entry.capture());
        assertThat(entry.getValue().fromStatus()).isNull();
        assertThat(entry.getValue().toStatus()).isEqualTo(VehicleStatus.ACTIVE);
        assertThat(entry.getValue().cause()).isEqualTo(StatusChangeCause.VEHICLE_CREATED);
        assertThat(entry.getValue().vehicleId()).isEqualTo(result.vehicleId());
    }

    @Test
    void execute_plateInUse_throwsPlateAlreadyExistsWithoutSaving() {
        when(vehicles.existsActiveByPlate("ABC123")).thenReturn(true);

        assertThatThrownBy(() -> service.execute(command("ABC123")))
                .isInstanceOf(PlateAlreadyExistsException.class);

        verify(vehicles, never()).save(any());
        verifyNoInteractions(history);
    }

    @Test
    void execute_invalidData_throwsValidationWithoutTouchingRepository() {
        var invalid = new RegisterVehicleCommand("zz", null, "T", "Hilux", 1900, null, null, null);

        assertThatThrownBy(() -> service.execute(invalid)).isInstanceOf(ValidationException.class);

        verifyNoInteractions(vehicles, history);
    }
}
