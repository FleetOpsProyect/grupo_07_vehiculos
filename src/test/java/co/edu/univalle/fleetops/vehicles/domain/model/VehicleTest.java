package co.edu.univalle.fleetops.vehicles.domain.model;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.NOW;
import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.activeVehicle;
import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.details;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.univalle.fleetops.vehicles.domain.exception.InvalidStatusTransitionException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VehicleTest {

    @Test
    void register_createsActiveVehicleWithOneDecimalOdometer() {
        Vehicle vehicle = Vehicle.register("ABC123", VehicleType.CAR, details(), new BigDecimal("15200"), NOW);

        assertThat(vehicle.id()).isNotNull();
        assertThat(vehicle.plate()).isEqualTo("ABC123");
        assertThat(vehicle.type()).isEqualTo(VehicleType.CAR);
        assertThat(vehicle.details()).isEqualTo(details());
        assertThat(vehicle.odometerKm()).isEqualByComparingTo("15200.0").hasScaleOf(1);
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.ACTIVE);
        assertThat(vehicle.blocks()).isEmpty();
        assertThat(vehicle.activeRouteId()).isNull();
        assertThat(vehicle.isDeleted()).isFalse();
        assertThat(vehicle.createdAt()).isEqualTo(NOW);
        assertThat(vehicle.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void restore_keepsStoredState() {
        UUID id = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        VehicleBlock block = VehicleBlock.of(BlockType.DOCUMENT, "SOAT", null, NOW);

        Vehicle vehicle = Vehicle.restore(id, "XYZ987", VehicleType.CAR, details(), new BigDecimal("10.5"),
                VehicleStatus.INACTIVE_EXPIRED_DOCUMENTS, List.of(block), routeId, false, NOW, NOW.plusDays(1));

        assertThat(vehicle.id()).isEqualTo(id);
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.INACTIVE_EXPIRED_DOCUMENTS);
        assertThat(vehicle.blocks()).containsExactly(block);
        assertThat(vehicle.activeRouteId()).isEqualTo(routeId);
        assertThat(vehicle.updatedAt()).isEqualTo(NOW.plusDays(1));
    }

    @Test
    void update_replacesDetailsAndTouchesUpdatedAt() {
        Vehicle vehicle = activeVehicle();
        VehicleDetails newDetails = new VehicleDetails("Toyota", "Hilux SR", 2023, "Silver", null);

        vehicle.update(newDetails, NOW);

        assertThat(vehicle.details()).isEqualTo(newDetails);
        assertThat(vehicle.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void startMaintenance_fromActive_addsBlockAndChangesStatus() {
        Vehicle vehicle = activeVehicle();
        UUID maintenanceId = UUID.randomUUID();

        boolean changed = vehicle.startMaintenance(maintenanceId, NOW);

        assertThat(changed).isTrue();
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.IN_MAINTENANCE);
        assertThat(vehicle.blocks()).singleElement().satisfies(block -> {
            assertThat(block.blockKey()).isEqualTo("MAINTENANCE:" + maintenanceId);
            assertThat(block.type()).isEqualTo(BlockType.MAINTENANCE);
            assertThat(block.reason()).isEqualTo("PREVENTIVE");
        });
        assertThat(vehicle.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void startMaintenance_sameMaintenanceId_changesNothing() {
        Vehicle vehicle = activeVehicle();
        UUID maintenanceId = UUID.randomUUID();
        vehicle.startMaintenance(maintenanceId, NOW);

        boolean changed = vehicle.startMaintenance(maintenanceId, NOW.plusHours(1));

        assertThat(changed).isFalse();
        assertThat(vehicle.blocks()).hasSize(1);
        assertThat(vehicle.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void startMaintenance_whenNotActive_throwsInvalidTransition() {
        Vehicle vehicle = Vehicle.restore(UUID.randomUUID(), "ABC123", VehicleType.CAR, details(),
                BigDecimal.ZERO, VehicleStatus.ON_ROUTE, List.of(), UUID.randomUUID(), false, NOW, NOW);

        assertThatThrownBy(() -> vehicle.startMaintenance(UUID.randomUUID(), NOW))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("ON_ROUTE");
    }

    @Test
    void markDeleted_setsDeletedStatusAndKeepsRecord() {
        Vehicle vehicle = activeVehicle();

        vehicle.markDeleted(NOW);

        assertThat(vehicle.isDeleted()).isTrue();
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.DELETED);
        assertThat(vehicle.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void blocks_isReadOnly() {
        Vehicle vehicle = activeVehicle();

        assertThatThrownBy(() -> vehicle.blocks().add(VehicleBlock.of(BlockType.INCIDENT, "inc-1", null, NOW)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
