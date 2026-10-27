package co.edu.univalle.fleetops.vehicles.infrastructure.persistence.adapter;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.NOW;
import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.activeVehicle;
import static org.assertj.core.api.Assertions.assertThat;

import co.edu.univalle.fleetops.vehicles.infrastructure.support.PostgresIntegrationTest;
import co.edu.univalle.fleetops.vehicles.application.port.out.StatusHistoryRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusChangeCause;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusHistoryEntry;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class StatusHistoryRepositoryAdapterTest extends PostgresIntegrationTest {

    @Autowired
    private StatusHistoryRepository history;
    @Autowired
    private VehicleRepository vehicles;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void save_storesEntryWithAllColumns() {
        Vehicle vehicle = vehicles.save(activeVehicle());

        history.save(StatusHistoryEntry.of(vehicle.id(), null, VehicleStatus.ACTIVE,
                StatusChangeCause.VEHICLE_CREATED, "src-1", NOW));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM status_history");
        assertThat(row.get("vehicle_id")).isEqualTo(vehicle.id());
        assertThat(row.get("from_status")).isNull();
        assertThat(row.get("to_status")).isEqualTo("ACTIVE");
        assertThat(row.get("cause")).isEqualTo("VEHICLE_CREATED");
        assertThat(row.get("source_id")).isEqualTo("src-1");
    }
}
