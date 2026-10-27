package co.edu.univalle.fleetops.vehicles.application.result;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.NOW;
import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.activeVehicle;
import static org.assertj.core.api.Assertions.assertThat;

import co.edu.univalle.fleetops.vehicles.domain.model.BlockType;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResultsTest {

    @Test
    void vehicleResult_from_mapsAllFieldsAndBlocks() {
        Vehicle vehicle = activeVehicle();
        UUID maintenanceId = UUID.randomUUID();
        vehicle.startMaintenance(maintenanceId, NOW);

        VehicleResult result = VehicleResult.from(vehicle);

        assertThat(result.vehicleId()).isEqualTo(vehicle.id());
        assertThat(result.plate()).isEqualTo("ABC123");
        assertThat(result.vehicleType()).isEqualTo(VehicleType.PICKUP_TRUCK);
        assertThat(result.brand()).isEqualTo("Toyota");
        assertThat(result.model()).isEqualTo("Hilux");
        assertThat(result.year()).isEqualTo(2023);
        assertThat(result.color()).isEqualTo("White");
        assertThat(result.notes()).isEqualTo("Assigned to Cali branch");
        assertThat(result.odometerKm()).isEqualByComparingTo("15200.0");
        assertThat(result.status()).isEqualTo(VehicleStatus.IN_MAINTENANCE);
        assertThat(result.activeBlocks()).singleElement().satisfies(block -> {
            assertThat(block.blockKey()).isEqualTo("MAINTENANCE:" + maintenanceId);
            assertThat(block.type()).isEqualTo(BlockType.MAINTENANCE);
            assertThat(block.sourceId()).isEqualTo(maintenanceId.toString());
            assertThat(block.reason()).isEqualTo("PREVENTIVE");
            assertThat(block.createdAt()).isEqualTo(NOW);
        });
        assertThat(result.activeRouteId()).isNull();
        assertThat(result.deleted()).isFalse();
        assertThat(result.createdAt()).isEqualTo(vehicle.createdAt());
        assertThat(result.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void vehicleSummaryResult_from_mapsListFields() {
        Vehicle vehicle = activeVehicle();

        VehicleSummaryResult result = VehicleSummaryResult.from(vehicle);

        assertThat(result).isEqualTo(new VehicleSummaryResult(vehicle.id(), "ABC123", VehicleType.PICKUP_TRUCK,
                "Toyota", "Hilux", 2023, vehicle.odometerKm(), VehicleStatus.ACTIVE));
    }

    @Test
    void deletedVehicleResult_from_mapsDeletedVehicle() {
        Vehicle vehicle = activeVehicle();
        vehicle.markDeleted(NOW);

        DeletedVehicleResult result = DeletedVehicleResult.from(vehicle);

        assertThat(result).isEqualTo(new DeletedVehicleResult(vehicle.id(), "ABC123", VehicleStatus.DELETED,
                true, NOW));
    }

    @Test
    void pageResult_map_convertsContentKeepingPaging() {
        var page = new PageResult<>(List.of(1, 2), 3, 20, 42L);

        PageResult<String> mapped = page.map(n -> "n" + n);

        assertThat(mapped).isEqualTo(new PageResult<>(List.of("n1", "n2"), 3, 20, 42L));
    }
}
