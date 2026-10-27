package co.edu.univalle.fleetops.vehicles.application.command;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChangeToMaintenanceCommandTest {

    @Test
    void constructor_nullMaintenanceId_throws() {
        UUID vehicleId = UUID.randomUUID();

        assertThatThrownBy(() -> new ChangeToMaintenanceCommand(vehicleId, VehicleStatus.IN_MAINTENANCE, null))
                .isInstanceOf(NullPointerException.class);
    }
}
