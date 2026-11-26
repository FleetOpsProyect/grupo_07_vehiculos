package co.edu.univalle.fleetops.vehicles.application.event;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.NOW;
import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.activeVehicle;
import static org.assertj.core.api.Assertions.assertThat;

import co.edu.univalle.fleetops.vehicles.domain.model.BlockType;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleBlock;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleDetails;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VehicleEventsTest {

    @Test
    void created_hasTheFullRecordInContractOrderAndVersionZero() {
        Vehicle vehicle = activeVehicle();

        Map<String, Object> data = VehicleEvents.created(vehicle);

        assertThat(data.keySet()).containsExactly("vehicle_id", "plate", "vehicle_type", "brand", "model", "year",
                "color", "notes", "odometer_km", "status", "version_number");
        assertThat(data)
                .containsEntry("vehicle_id", vehicle.id())
                .containsEntry("plate", "ABC123")
                .containsEntry("vehicle_type", "PICKUP_TRUCK")
                .containsEntry("brand", "Toyota")
                .containsEntry("year", 2023)
                .containsEntry("odometer_km", new BigDecimal("15200.0"))
                .containsEntry("status", "ACTIVE")
                .containsEntry("version_number", 0L);
    }

    @Test
    void created_withoutColorAndNotes_keepsTheKeysWithNull() {
        Vehicle vehicle = Vehicle.register("XYZ987", VehicleType.CAR,
                new VehicleDetails("Mazda", "3", 2022, null, null), BigDecimal.TEN, NOW);

        Map<String, Object> data = VehicleEvents.created(vehicle);

        assertThat(data).containsEntry("color", null).containsEntry("notes", null);
    }

    @Test
    void updated_hasTheChangedFieldsAndTheFullRecordWithoutOdometerOrStatus() {
        Vehicle vehicle = activeVehicle();
        List<String> changed = new ArrayList<>(vehicle.update(
                new VehicleDetails("Toyota", "Hilux SR", 2023, "Silver", "Repainted"), NOW));

        Map<String, Object> data = VehicleEvents.updated(vehicle, changed);
        changed.clear();

        assertThat(data.keySet()).containsExactly("vehicle_id", "changed_fields", "plate", "vehicle_type", "brand",
                "model", "year", "color", "notes", "version_number");
        assertThat(data)
                .containsEntry("changed_fields", List.of("model", "color", "notes"))
                .containsEntry("model", "Hilux SR")
                .containsEntry("version_number", 1L);
    }

    @Test
    void deleted_usesTheMomentOfTheDeletionAsDeletedAt() {
        Vehicle vehicle = activeVehicle();
        vehicle.markDeleted(NOW);

        Map<String, Object> data = VehicleEvents.deleted(vehicle);

        assertThat(data).containsExactly(
                Map.entry("vehicle_id", vehicle.id()),
                Map.entry("plate", "ABC123"),
                Map.entry("deleted_at", NOW),
                Map.entry("version_number", 1L));
    }

    @Test
    void statusChanged_blockedVehicle_listsItsBlocksAndIsNotAssignable() {
        Vehicle vehicle = activeVehicle();
        UUID maintenanceId = UUID.randomUUID();
        vehicle.startMaintenance(maintenanceId, NOW);

        Map<String, Object> data = VehicleEvents.statusChanged(vehicle, VehicleStatus.ACTIVE,
                "MAINTENANCE", VehicleEvents.BLOCK_ADDED, maintenanceId.toString());

        assertThat(data.keySet()).containsExactly("vehicle_id", "previous_status", "status", "cause",
                "active_blocks", "is_assignable", "version_number");
        assertThat(data)
                .containsEntry("previous_status", "ACTIVE")
                .containsEntry("status", "IN_MAINTENANCE")
                .containsEntry("cause", Map.of("type", "MAINTENANCE", "action", "BLOCK_ADDED",
                        "source_id", maintenanceId.toString()))
                .containsEntry("active_blocks",
                        List.of(Map.of("block_key", "MAINTENANCE:" + maintenanceId, "type", "MAINTENANCE")))
                .containsEntry("is_assignable", false)
                .containsEntry("version_number", 1L);
    }

    @Test
    void statusChanged_activeVehicleWithoutBlocks_isAssignable() {
        VehicleBlock removed = VehicleBlock.of(BlockType.INCIDENT, "inc-1", null, NOW);
        Vehicle vehicle = Vehicle.restore(UUID.randomUUID(), "ABC123", VehicleType.CAR, activeVehicle().details(),
                BigDecimal.ZERO, VehicleStatus.ACTIVE, List.of(), null, false, 3, NOW, NOW);

        Map<String, Object> data = VehicleEvents.statusChanged(vehicle, VehicleStatus.BLOCKED,
                "INCIDENT", "BLOCK_REMOVED", removed.sourceId());

        assertThat(data)
                .containsEntry("active_blocks", List.of())
                .containsEntry("is_assignable", true)
                .containsEntry("version_number", 3L);
    }

    @Test
    void odometerUpdated_hasThePreviousDistanceAndNewOdometer() {
        Vehicle vehicle = activeVehicle();
        UUID routeId = UUID.randomUUID();
        vehicle.finishRoute(routeId, new BigDecimal("15.5"), NOW);

        Map<String, Object> data = VehicleEvents.odometerUpdated(vehicle, routeId, new BigDecimal("15200.0"),
                new BigDecimal("15.5"));

        assertThat(data).containsExactly(
                Map.entry("vehicle_id", vehicle.id()),
                Map.entry("route_id", routeId),
                Map.entry("previous_odometer_km", new BigDecimal("15200.0")),
                Map.entry("distance_km", new BigDecimal("15.5")),
                Map.entry("odometer_km", new BigDecimal("15215.5")),
                Map.entry("version_number", 1L));
    }
}
