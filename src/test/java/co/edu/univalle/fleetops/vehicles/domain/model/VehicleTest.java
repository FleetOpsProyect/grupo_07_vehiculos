package co.edu.univalle.fleetops.vehicles.domain.model;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.NOW;
import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.activeVehicle;
import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.details;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.univalle.fleetops.vehicles.domain.exception.InvalidStatusTransitionException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
        assertThat(vehicle.versionNumber()).isZero();
    }

    @Test
    void restore_keepsStoredState() {
        UUID id = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        VehicleBlock block = VehicleBlock.of(BlockType.DOCUMENT, "SOAT", null, NOW);

        Vehicle vehicle = Vehicle.restore(id, "XYZ987", VehicleType.CAR, details(), new BigDecimal("10.5"),
                VehicleStatus.INACTIVE_EXPIRED_DOCUMENTS, List.of(block), routeId, false, 6, NOW, NOW.plusDays(1));

        assertThat(vehicle.id()).isEqualTo(id);
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.INACTIVE_EXPIRED_DOCUMENTS);
        assertThat(vehicle.blocks()).containsExactly(block);
        assertThat(vehicle.activeRouteId()).isEqualTo(routeId);
        assertThat(vehicle.updatedAt()).isEqualTo(NOW.plusDays(1));
        assertThat(vehicle.versionNumber()).isEqualTo(6);
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
                BigDecimal.ZERO, VehicleStatus.ON_ROUTE, List.of(), UUID.randomUUID(), false, 0, NOW, NOW);

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

    @Test
    void update_returnsTheChangedFieldsInOrderAndRaisesTheVersion() {
        Vehicle vehicle = activeVehicle();

        List<String> changed = vehicle.update(new VehicleDetails("Chevrolet", "Hilux", 2024, "White", "Other"),
                NOW.plusDays(1));

        assertThat(changed).containsExactly("brand", "year", "notes");
        assertThat(vehicle.versionNumber()).isEqualTo(1);
        assertThat(vehicle.updatedAt()).isEqualTo(NOW.plusDays(1));
    }

    @Test
    void update_nullAndValueCount_asChanges() {
        Vehicle vehicle = activeVehicle();

        List<String> changed = vehicle.update(new VehicleDetails("Toyota", "Hilux", 2023, null,
                "Assigned to Cali branch"), NOW);

        assertThat(changed).containsExactly("color");
    }

    @Test
    void update_sameData_changesNothing() {
        Vehicle vehicle = activeVehicle();
        OffsetDateTime before = vehicle.updatedAt();

        List<String> changed = vehicle.update(details(), NOW.plusDays(1));

        assertThat(changed).isEmpty();
        assertThat(vehicle.versionNumber()).isZero();
        assertThat(vehicle.updatedAt()).isEqualTo(before);
    }

    @Test
    void startMaintenance_raisesTheVersionOnlyWhenItChanges() {
        Vehicle vehicle = activeVehicle();
        UUID maintenanceId = UUID.randomUUID();

        vehicle.startMaintenance(maintenanceId, NOW);
        vehicle.startMaintenance(maintenanceId, NOW.plusHours(1));

        assertThat(vehicle.versionNumber()).isEqualTo(1);
    }

    @Test
    void markDeleted_raisesTheVersion() {
        Vehicle vehicle = activeVehicle();
        vehicle.update(new VehicleDetails("Toyota", "Hilux SR", 2023, "White", null), NOW);

        vehicle.markDeleted(NOW.plusDays(1));

        assertThat(vehicle.versionNumber()).isEqualTo(2);
    }

    @Test
    void addBlock_severeIncident_blocksAnyStatusAndRaisesTheVersion() {
        Vehicle vehicle = activeVehicle();
        vehicle.startMaintenance(UUID.randomUUID(), NOW);

        boolean added = vehicle.addBlock(BlockType.INCIDENT, "inc-1", "SEVERE_INCIDENT", NOW.plusHours(1));

        assertThat(added).isTrue();
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.BLOCKED);
        assertThat(vehicle.blocks()).extracting(VehicleBlock::blockKey).contains("INCIDENT:inc-1");
        assertThat(vehicle.versionNumber()).isEqualTo(2);
        assertThat(vehicle.updatedAt()).isEqualTo(NOW.plusHours(1));
    }

    @Test
    void addBlock_sameKeyTwice_changesNothingTheSecondTime() {
        Vehicle vehicle = activeVehicle();
        vehicle.addBlock(BlockType.DOCUMENT, "doc-1", "SOAT", NOW);

        boolean added = vehicle.addBlock(BlockType.DOCUMENT, "doc-1", "SOAT", NOW.plusHours(1));

        assertThat(added).isFalse();
        assertThat(vehicle.blocks()).hasSize(1);
        assertThat(vehicle.versionNumber()).isEqualTo(1);
    }

    @Test
    void removeBlocks_lastBlock_returnsToActive() {
        Vehicle vehicle = activeVehicle();
        vehicle.addBlock(BlockType.INCIDENT, "inc-1", null, NOW);
        vehicle.addBlock(BlockType.DOCUMENT, "doc-1", null, NOW);

        boolean removed = vehicle.removeBlocks(List.of("INCIDENT:inc-1", "DOCUMENT:doc-1", "MAINTENANCE:x"), NOW);

        assertThat(removed).isTrue();
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.ACTIVE);
        assertThat(vehicle.blocks()).isEmpty();
        assertThat(vehicle.versionNumber()).isEqualTo(3);
    }

    @Test
    void removeBlocks_oneOfTwo_keepsTheStatusOfTheRemainingBlock() {
        Vehicle vehicle = activeVehicle();
        vehicle.addBlock(BlockType.INCIDENT, "inc-1", null, NOW);
        vehicle.addBlock(BlockType.DOCUMENT, "doc-1", null, NOW);

        vehicle.removeBlocks(List.of("INCIDENT:inc-1"), NOW);

        assertThat(vehicle.status()).isEqualTo(VehicleStatus.INACTIVE_EXPIRED_DOCUMENTS);
    }

    @Test
    void removeBlocks_missingKey_changesNothing() {
        Vehicle vehicle = activeVehicle();

        boolean removed = vehicle.removeBlocks(List.of("INCIDENT:inc-1"), NOW);

        assertThat(removed).isFalse();
        assertThat(vehicle.versionNumber()).isZero();
    }

    @Test
    void startRoute_activeVehicle_goesOnRouteAndRepeatingItChangesNothing() {
        Vehicle vehicle = activeVehicle();
        UUID routeId = UUID.randomUUID();

        boolean started = vehicle.startRoute(routeId, NOW);
        boolean again = vehicle.startRoute(routeId, NOW.plusMinutes(5));

        assertThat(started).isTrue();
        assertThat(again).isFalse();
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.ON_ROUTE);
        assertThat(vehicle.activeRouteId()).isEqualTo(routeId);
        assertThat(vehicle.versionNumber()).isEqualTo(1);
    }

    @Test
    void startRoute_blockedVehicle_keepsTheBlockStatus() {
        Vehicle vehicle = activeVehicle();
        vehicle.addBlock(BlockType.INCIDENT, "inc-1", null, NOW);

        vehicle.startRoute(UUID.randomUUID(), NOW);

        assertThat(vehicle.status()).isEqualTo(VehicleStatus.BLOCKED);
    }

    @Test
    void finishRoute_currentRoute_addsTheDistanceAndReturnsToActive() {
        Vehicle vehicle = activeVehicle();
        UUID routeId = UUID.randomUUID();
        vehicle.startRoute(routeId, NOW);

        vehicle.finishRoute(routeId, new BigDecimal("15.55"), NOW.plusHours(1));

        assertThat(vehicle.odometerKm()).isEqualByComparingTo("15215.6").hasScaleOf(1);
        assertThat(vehicle.activeRouteId()).isNull();
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.ACTIVE);
        assertThat(vehicle.versionNumber()).isEqualTo(2);
    }

    @Test
    void finishRoute_otherRoute_addsTheDistanceButKeepsTheCurrentRoute() {
        Vehicle vehicle = activeVehicle();
        UUID current = UUID.randomUUID();
        vehicle.startRoute(current, NOW);

        vehicle.finishRoute(UUID.randomUUID(), new BigDecimal("10"), NOW);

        assertThat(vehicle.odometerKm()).isEqualByComparingTo("15210.0");
        assertThat(vehicle.activeRouteId()).isEqualTo(current);
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.ON_ROUTE);
    }

    @Test
    void finishRoute_deletedVehicle_addsTheDistanceAndStaysDeleted() {
        Vehicle vehicle = activeVehicle();
        vehicle.markDeleted(NOW);

        vehicle.finishRoute(UUID.randomUUID(), new BigDecimal("5"), NOW);

        assertThat(vehicle.odometerKm()).isEqualByComparingTo("15205.0");
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.DELETED);
    }
}
