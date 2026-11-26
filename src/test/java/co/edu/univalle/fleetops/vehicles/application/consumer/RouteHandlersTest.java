package co.edu.univalle.fleetops.vehicles.application.consumer;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.NOW;
import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.activeVehicle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.univalle.fleetops.vehicles.application.event.VehicleEvents;
import co.edu.univalle.fleetops.vehicles.application.exception.InvalidEventDataException;
import co.edu.univalle.fleetops.vehicles.application.port.out.AppliedRouteRepository;
import co.edu.univalle.fleetops.vehicles.domain.model.BlockType;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusChangeCause;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class RouteHandlersTest extends ConsumerTestSupport {

    @Mock
    private AppliedRouteRepository routes;

    private RouteStartedHandler started;
    private RouteFinishedHandler finished;
    private final UUID routeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        started = new RouteStartedHandler(vehicles, history, outbox, clock, routes);
        finished = new RouteFinishedHandler(vehicles, history, outbox, clock, routes);
    }

    private Object[] start(Vehicle vehicle) {
        return new Object[] {"route_id", routeId.toString(), "vehicle_id", vehicle.id().toString()};
    }

    private Object[] finish(Vehicle vehicle, Object distance) {
        return new Object[] {"route_id", routeId.toString(), "vehicle_id", vehicle.id().toString(),
            "distance_km", distance, "route_status", "COMPLETED"};
    }

    @Test
    void eventTypes_areTheRouteEvents() {
        assertThat(started.eventType()).isEqualTo("route_started_event");
        assertThat(finished.eventType()).isEqualTo("route_finished_event");
    }

    @Test
    void started_activeVehicle_goesOnRouteAndPublishesStatusChanged() {
        Vehicle vehicle = activeVehicle();
        stored(vehicle);

        boolean changed = started.handle(event(started.eventType(), start(vehicle)));

        assertThat(changed).isTrue();
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.ON_ROUTE);
        assertThat(historyEntry().cause()).isEqualTo(StatusChangeCause.ROUTE_STARTED);
        assertThat(recordedData(VehicleEvents.STATUS_CHANGED, vehicle))
                .containsEntry("status", "ON_ROUTE")
                .containsEntry("cause", Map.of("type", "ROUTE", "action", "ROUTE_STARTED",
                        "source_id", routeId.toString()))
                .containsEntry("is_assignable", false);
    }

    @Test
    void started_blockedVehicle_savesTheRouteWithoutPublishingBecauseTheStatusStays() {
        Vehicle vehicle = activeVehicle();
        vehicle.addBlock(BlockType.INCIDENT, "inc-1", null, NOW);
        stored(vehicle);

        boolean changed = started.handle(event(started.eventType(), start(vehicle)));

        assertThat(changed).isTrue();
        verify(vehicles).save(vehicle);
        verifyNoInteractions(history, outbox);
    }

    @Test
    void started_sameRouteAgain_changesNothing() {
        Vehicle vehicle = activeVehicle();
        vehicle.startRoute(routeId, NOW);
        stored(vehicle);

        assertThat(started.handle(event(started.eventType(), start(vehicle)))).isFalse();
        nothingChanged();
    }

    @Test
    void started_routeAlreadyFinished_isIgnored() {
        Vehicle vehicle = activeVehicle();
        stored(vehicle);
        when(routes.isApplied(routeId)).thenReturn(true);

        assertThat(started.handle(event(started.eventType(), start(vehicle)))).isFalse();
        nothingChanged();
    }

    @Test
    void started_unknownVehicle_isIgnored() {
        UUID id = UUID.randomUUID();
        unknown(id);

        assertThat(started.handle(event(started.eventType(), "route_id", routeId.toString(),
                "vehicle_id", id.toString()))).isFalse();
    }

    @Test
    void finished_currentRoute_addsTheDistancePublishesOdometerAndStatusChanged() {
        Vehicle vehicle = activeVehicle();
        vehicle.startRoute(routeId, NOW);
        stored(vehicle);
        when(routes.markApplied(routeId, vehicle.id(), new BigDecimal("15.5"), NOW)).thenReturn(true);

        boolean changed = finished.handle(event(finished.eventType(), finish(vehicle, 15.5)));

        assertThat(changed).isTrue();
        assertThat(vehicle.odometerKm()).isEqualByComparingTo("15215.5");
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.ACTIVE);
        verify(outbox).record(eq(VehicleEvents.ODOMETER_UPDATED), eq(vehicle.id()), data.capture());
        assertThat(data.getValue())
                .containsEntry("previous_odometer_km", new BigDecimal("15200.0"))
                .containsEntry("distance_km", new BigDecimal("15.5"))
                .containsEntry("odometer_km", new BigDecimal("15215.5"))
                .containsEntry("version_number", 2L);
        verify(outbox).record(eq(VehicleEvents.STATUS_CHANGED), eq(vehicle.id()), data.capture());
        assertThat(data.getValue())
                .containsEntry("previous_status", "ON_ROUTE")
                .containsEntry("cause", Map.of("type", "ROUTE", "action", "ROUTE_FINISHED",
                        "source_id", routeId.toString()));
        assertThat(historyEntry().cause()).isEqualTo(StatusChangeCause.ROUTE_FINISHED);
    }

    @Test
    void finished_deletedVehicle_stillAddsTheDistanceWithoutStatusChange() {
        Vehicle vehicle = activeVehicle();
        vehicle.markDeleted(NOW);
        stored(vehicle);
        when(routes.markApplied(any(), any(), any(), any())).thenReturn(true);

        boolean changed = finished.handle(event(finished.eventType(), finish(vehicle, 6)));

        assertThat(changed).isTrue();
        assertThat(vehicle.odometerKm()).isEqualByComparingTo("15206.0");
        verify(outbox).record(eq(VehicleEvents.ODOMETER_UPDATED), eq(vehicle.id()), any());
        verify(outbox, never()).record(eq(VehicleEvents.STATUS_CHANGED), any(), any());
        verifyNoInteractions(history);
    }

    @Test
    void finished_routeAlreadyApplied_addsNothing() {
        Vehicle vehicle = activeVehicle();
        stored(vehicle);
        when(routes.markApplied(any(), any(), any(), any())).thenReturn(false);

        assertThat(finished.handle(event(finished.eventType(), finish(vehicle, 6)))).isFalse();
        assertThat(vehicle.odometerKm()).isEqualByComparingTo("15200.0");
        nothingChanged();
    }

    @Test
    void finished_unknownVehicle_isIgnoredWithoutMarkingTheRoute() {
        UUID id = UUID.randomUUID();
        unknown(id);

        assertThat(finished.handle(event(finished.eventType(), "route_id", routeId.toString(),
                "vehicle_id", id.toString(), "distance_km", 3))).isFalse();
        verifyNoInteractions(routes);
    }

    @Test
    void finished_negativeDistance_isInvalid() {
        Vehicle vehicle = activeVehicle();
        var invalid = event(finished.eventType(), finish(vehicle, -1));

        assertThatThrownBy(() -> finished.handle(invalid)).isInstanceOf(InvalidEventDataException.class);
        verifyNoInteractions(routes, vehicles);
    }

    @Test
    void started_routeIdNotUuid_isInvalid() {
        var invalid = event(started.eventType(), "route_id", "ruta-1", "vehicle_id", UUID.randomUUID().toString());

        assertThatThrownBy(() -> started.handle(invalid)).isInstanceOf(InvalidEventDataException.class);
        verify(routes, never()).isApplied(any());
        verify(vehicles, never()).findById(any());
        verify(history, never()).save(any());
        verify(outbox, never()).record(anyString(), any(), any());
    }
}
