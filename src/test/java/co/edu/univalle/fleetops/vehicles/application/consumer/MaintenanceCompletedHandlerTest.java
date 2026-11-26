package co.edu.univalle.fleetops.vehicles.application.consumer;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.NOW;
import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.activeVehicle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.univalle.fleetops.vehicles.application.event.VehicleEvents;
import co.edu.univalle.fleetops.vehicles.application.exception.InvalidEventDataException;
import co.edu.univalle.fleetops.vehicles.domain.model.BlockType;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusChangeCause;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaintenanceCompletedHandlerTest extends ConsumerTestSupport {

    private MaintenanceCompletedHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MaintenanceCompletedHandler(vehicles, history, outbox, clock);
    }

    @Test
    void eventType_isMaintenanceCompleted() {
        assertThat(handler.eventType()).isEqualTo("maintenance_completed_event");
    }

    @Test
    void handle_preventive_removesTheMaintenanceBlockAndReturnsToActive() {
        Vehicle vehicle = activeVehicle();
        UUID orderId = UUID.randomUUID();
        vehicle.startMaintenance(orderId, NOW);
        stored(vehicle);

        boolean changed = handler.handle(event(handler.eventType(), "vehicle_id", vehicle.id().toString(),
                "maintenance_order_id", orderId.toString(), "incident_id", null, "maintenance_type", "PREVENTIVE"));

        assertThat(changed).isTrue();
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.ACTIVE);
        assertThat(historyEntry().cause()).isEqualTo(StatusChangeCause.MAINTENANCE_COMPLETED);
        assertThat(recordedData(VehicleEvents.STATUS_CHANGED, vehicle))
                .containsEntry("previous_status", "IN_MAINTENANCE")
                .containsEntry("status", "ACTIVE")
                .containsEntry("cause", Map.of("type", "MAINTENANCE", "action", "BLOCK_REMOVED",
                        "source_id", orderId.toString()))
                .containsEntry("active_blocks", List.of())
                .containsEntry("is_assignable", true);
    }

    @Test
    void handle_corrective_alsoRemovesTheIncidentBlock() {
        Vehicle vehicle = activeVehicle();
        vehicle.addBlock(BlockType.INCIDENT, "inc-1", null, NOW);
        vehicle.addBlock(BlockType.MAINTENANCE, "order-1", "CORRECTIVE", NOW);
        stored(vehicle);

        handler.handle(event(handler.eventType(), "vehicle_id", vehicle.id().toString(),
                "maintenance_order_id", "order-1", "incident_id", "inc-1"));

        assertThat(vehicle.blocks()).isEmpty();
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.ACTIVE);
    }

    @Test
    void handle_maintenanceAlreadyRemoved_changesNothing() {
        Vehicle vehicle = activeVehicle();
        stored(vehicle);

        boolean changed = handler.handle(event(handler.eventType(), "vehicle_id", vehicle.id().toString(),
                "maintenance_order_id", "order-1"));

        assertThat(changed).isFalse();
        nothingChanged();
    }

    @Test
    void handle_unknownVehicle_isIgnored() {
        UUID id = UUID.randomUUID();
        unknown(id);

        assertThat(handler.handle(event(handler.eventType(), "vehicle_id", id.toString(),
                "maintenance_order_id", "order-1"))).isFalse();
        nothingChanged();
    }

    @Test
    void handle_withoutMaintenanceOrderId_isInvalid() {
        var invalid = event(handler.eventType(), "vehicle_id", UUID.randomUUID().toString());

        assertThatThrownBy(() -> handler.handle(invalid)).isInstanceOf(InvalidEventDataException.class);
    }
}
