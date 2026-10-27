package co.edu.univalle.fleetops.vehicles.application.usecase;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.activeVehicle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleResult;
import co.edu.univalle.fleetops.vehicles.domain.exception.VehicleNotFoundException;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetVehicleServiceTest {

    @Mock
    private VehicleRepository vehicles;
    @InjectMocks
    private GetVehicleService service;

    @Test
    void execute_existingVehicle_returnsDetail() {
        Vehicle vehicle = activeVehicle();
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));

        VehicleResult result = service.execute(vehicle.id());

        assertThat(result.vehicleId()).isEqualTo(vehicle.id());
    }

    @Test
    void execute_deletedVehicle_isStillReturned() {
        Vehicle vehicle = activeVehicle();
        vehicle.markDeleted(vehicle.updatedAt());
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));

        assertThat(service.execute(vehicle.id()).deleted()).isTrue();
    }

    @Test
    void execute_unknownId_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(vehicles.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(id)).isInstanceOf(VehicleNotFoundException.class);
    }
}
