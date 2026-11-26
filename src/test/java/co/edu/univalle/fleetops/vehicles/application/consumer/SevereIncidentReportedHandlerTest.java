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

class SevereIncidentReportedHandlerTest extends ConsumerTestSupport {

    private SevereIncidentReportedHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SevereIncidentReportedHandler(vehicles, history, outbox, clock);
    }

    private static Object[] incident(Vehicle vehicle, String incidentId) {
        return new Object[] {"vehicle_id", vehicle.id().toString(), "incident_id", incidentId,
            "severity", "severe"};
    }

    @Test
    void eventType_isTheSevereIncident() {
        assertThat(handler.eventType()).isEqualTo("severe_incident_reported_event");
    }

    @Test
    void handle_activeVehicle_blocksItRecordsHistoryAndPublishesStatusChanged() {
        Vehicle vehicle = activeVehicle();
        stored(vehicle);

        boolean changed = handler.handle(event(handler.eventType(), incident(vehicle, "inc-1")));

        assertThat(changed).isTrue();
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.BLOCKED);
        assertThat(historyEntry()).satisfies(row -> {
            assertThat(row.fromStatus()).isEqualTo(VehicleStatus.ACTIVE);
            assertThat(row.toStatus()).isEqualTo(VehicleStatus.BLOCKED);
            assertThat(row.cause()).isEqualTo(StatusChangeCause.INCIDENT_REPORTED);
            assertThat(row.sourceId()).isEqualTo("inc-1");
            assertThat(row.changedAt()).isEqualTo(NOW);
        });
        assertThat(recordedData(VehicleEvents.STATUS_CHANGED, vehicle))
                .containsEntry("previous_status", "ACTIVE")
                .containsEntry("status", "BLOCKED")
                .containsEntry("cause", Map.of("type", "INCIDENT", "action", "BLOCK_ADDED", "source_id", "inc-1"))
                .containsEntry("active_blocks", List.of(Map.of("block_key", "INCIDENT:inc-1", "type", "INCIDENT")))
                .containsEntry("version_number", 1L);
    }

    @Test
    void handle_sameIncidentAgain_changesNothing() {
        Vehicle vehicle = activeVehicle();
        vehicle.addBlock(BlockType.INCIDENT, "inc-1", null, NOW);
        stored(vehicle);

        boolean changed = handler.handle(event(handler.eventType(), incident(vehicle, "inc-1")));

        assertThat(changed).isFalse();
        nothingChanged();
    }

    @Test
    void handle_deletedVehicle_isIgnored() {
        Vehicle vehicle = activeVehicle();
        vehicle.markDeleted(NOW);
        stored(vehicle);

        assertThat(handler.handle(event(handler.eventType(), incident(vehicle, "inc-1")))).isFalse();
        nothingChanged();
    }

    @Test
    void handle_unknownVehicle_isIgnored() {
        UUID id = UUID.randomUUID();
        unknown(id);

        assertThat(handler.handle(event(handler.eventType(), "vehicle_id", id.toString(), "incident_id", "i")))
                .isFalse();
        nothingChanged();
    }

    @Test
    void handle_withoutIncidentId_isInvalid() {
        var invalid = event(handler.eventType(), "vehicle_id", UUID.randomUUID().toString());

        assertThatThrownBy(() -> handler.handle(invalid)).isInstanceOf(InvalidEventDataException.class);
        nothingChanged();
    }
}
