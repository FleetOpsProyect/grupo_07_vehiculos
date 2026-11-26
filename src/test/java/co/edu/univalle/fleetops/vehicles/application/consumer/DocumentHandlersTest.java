package co.edu.univalle.fleetops.vehicles.application.consumer;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.NOW;
import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.activeVehicle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import co.edu.univalle.fleetops.vehicles.application.event.VehicleEvents;
import co.edu.univalle.fleetops.vehicles.application.exception.InvalidEventDataException;
import co.edu.univalle.fleetops.vehicles.domain.model.BlockType;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusChangeCause;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DocumentHandlersTest extends ConsumerTestSupport {

    private DocumentExpiredHandler expired;
    private DocumentRenewedHandler renewed;

    @BeforeEach
    void setUp() {
        expired = new DocumentExpiredHandler(vehicles, history, outbox, clock);
        renewed = new DocumentRenewedHandler(vehicles, history, outbox, clock);
    }

    @Test
    void eventTypes_areTheDocumentEvents() {
        assertThat(expired.eventType()).isEqualTo("document_expired_event");
        assertThat(renewed.eventType()).isEqualTo("document_renewed_event");
    }

    @Test
    void expired_vehicleDocument_addsTheDocumentBlock() {
        Vehicle vehicle = activeVehicle();
        stored(vehicle);

        boolean changed = expired.handle(event(expired.eventType(), "owner_type", "VEHICLE",
                "owner_id", vehicle.id().toString(), "document_id", "doc-1", "document_type", "SOAT"));

        assertThat(changed).isTrue();
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.INACTIVE_EXPIRED_DOCUMENTS);
        assertThat(vehicle.blocks()).singleElement().satisfies(block -> {
            assertThat(block.blockKey()).isEqualTo("DOCUMENT:doc-1");
            assertThat(block.reason()).isEqualTo("SOAT");
        });
        assertThat(historyEntry().cause()).isEqualTo(StatusChangeCause.DOCUMENT_EXPIRED);
        assertThat(recordedData(VehicleEvents.STATUS_CHANGED, vehicle))
                .containsEntry("cause", Map.of("type", "DOCUMENT", "action", "BLOCK_ADDED", "source_id", "doc-1"));
    }

    @Test
    void expired_blockedVehicle_addsTheBlockAndPublishesAlthoughTheStatusStays() {
        Vehicle vehicle = activeVehicle();
        vehicle.addBlock(BlockType.INCIDENT, "inc-1", null, NOW);
        stored(vehicle);

        expired.handle(event(expired.eventType(), "owner_type", "VEHICLE", "owner_id", vehicle.id().toString(),
                "document_id", "doc-1"));

        assertThat(recordedData(VehicleEvents.STATUS_CHANGED, vehicle))
                .containsEntry("previous_status", "BLOCKED")
                .containsEntry("status", "BLOCKED");
    }

    @Test
    void expired_sameDocumentAgain_changesNothing() {
        Vehicle vehicle = activeVehicle();
        vehicle.addBlock(BlockType.DOCUMENT, "doc-1", "SOAT", NOW);
        stored(vehicle);

        assertThat(expired.handle(event(expired.eventType(), "owner_type", "VEHICLE",
                "owner_id", vehicle.id().toString(), "document_id", "doc-1"))).isFalse();
        nothingChanged();
    }

    @Test
    void expired_driverDocument_isIgnoredWithoutLookingForAVehicle() {
        boolean changed = expired.handle(event(expired.eventType(), "owner_type", "DRIVER",
                "owner_id", "driver-1", "document_id", "doc-1"));

        assertThat(changed).isFalse();
        verifyNoInteractions(vehicles, history, outbox);
    }

    @Test
    void expired_unknownVehicle_isIgnored() {
        UUID id = UUID.randomUUID();
        unknown(id);

        assertThat(expired.handle(event(expired.eventType(), "owner_type", "VEHICLE", "owner_id", id.toString(),
                "document_id", "doc-1"))).isFalse();
    }

    @Test
    void expired_withoutOwnerType_isInvalid() {
        var invalid = event(expired.eventType(), "owner_id", UUID.randomUUID().toString(), "document_id", "d");

        assertThatThrownBy(() -> expired.handle(invalid)).isInstanceOf(InvalidEventDataException.class);
    }

    @Test
    void renewed_vehicleDocument_removesThePreviousDocumentBlock() {
        Vehicle vehicle = activeVehicle();
        vehicle.addBlock(BlockType.DOCUMENT, "doc-1", "SOAT", NOW);
        stored(vehicle);

        boolean changed = renewed.handle(event(renewed.eventType(), "owner_type", "VEHICLE",
                "owner_id", vehicle.id().toString(), "document_id", "doc-2", "previous_document_id", "doc-1"));

        assertThat(changed).isTrue();
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.ACTIVE);
        assertThat(historyEntry().cause()).isEqualTo(StatusChangeCause.DOCUMENT_RENEWED);
        assertThat(recordedData(VehicleEvents.STATUS_CHANGED, vehicle))
                .containsEntry("cause", Map.of("type", "DOCUMENT", "action", "BLOCK_REMOVED", "source_id", "doc-1"))
                .containsEntry("is_assignable", true);
    }

    @Test
    void renewed_withoutThatBlock_changesNothing() {
        Vehicle vehicle = activeVehicle();
        stored(vehicle);

        assertThat(renewed.handle(event(renewed.eventType(), "owner_type", "VEHICLE",
                "owner_id", vehicle.id().toString(), "previous_document_id", "doc-1"))).isFalse();
        nothingChanged();
    }

    @Test
    void renewed_driverDocument_isIgnored() {
        assertThat(renewed.handle(event(renewed.eventType(), "owner_type", "DRIVER",
                "owner_id", "driver-1", "previous_document_id", "doc-1"))).isFalse();
        verifyNoInteractions(vehicles);
    }

    @Test
    void renewed_deletedVehicle_isIgnored() {
        Vehicle vehicle = activeVehicle();
        vehicle.markDeleted(NOW);
        stored(vehicle);

        assertThat(renewed.handle(event(renewed.eventType(), "owner_type", "VEHICLE",
                "owner_id", vehicle.id().toString(), "previous_document_id", "doc-1"))).isFalse();
    }
}
