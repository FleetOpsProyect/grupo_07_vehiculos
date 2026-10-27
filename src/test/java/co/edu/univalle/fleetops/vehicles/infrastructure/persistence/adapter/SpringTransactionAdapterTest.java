package co.edu.univalle.fleetops.vehicles.infrastructure.persistence.adapter;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.activeVehicle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.univalle.fleetops.vehicles.infrastructure.support.PostgresIntegrationTest;
import co.edu.univalle.fleetops.vehicles.application.port.out.TransactionPort;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SpringTransactionAdapterTest extends PostgresIntegrationTest {

    @Autowired
    private TransactionPort transaction;
    @Autowired
    private VehicleRepository vehicles;

    @Test
    void execute_returnsResultAndCommits() {
        Vehicle vehicle = activeVehicle();

        String result = transaction.execute(() -> {
            vehicles.save(vehicle);
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(vehicles.findById(vehicle.id())).isPresent();
    }

    @Test
    void execute_actionThrows_rollsBackEverything() {
        Vehicle vehicle = activeVehicle();

        assertThatThrownBy(() -> transaction.execute(() -> {
            vehicles.save(vehicle);
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(vehicles.findById(vehicle.id())).isEmpty();
    }
}
