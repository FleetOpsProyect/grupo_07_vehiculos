package co.edu.univalle.fleetops.vehicles.application.consumer;

import co.edu.univalle.fleetops.vehicles.application.event.EventData;
import co.edu.univalle.fleetops.vehicles.application.event.IncomingEvent;
import co.edu.univalle.fleetops.vehicles.application.event.VehicleEvents;
import co.edu.univalle.fleetops.vehicles.application.port.out.EventOutbox;
import co.edu.univalle.fleetops.vehicles.application.port.out.StatusHistoryRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.AppliedRouteRepository;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusChangeCause;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code route_finished_event} (Telemetría, G6): suma {@code distance_km} al odómetro una sola vez por
 * {@code route_id}, libera la ruta y publica {@code vehicle_odometer_updated_event}. También suma en un
 * vehículo eliminado. Si el vehículo sale de ON_ROUTE, publica además {@code vehicle_status_changed_event}.
 */
public class RouteFinishedHandler extends VehicleEventHandler {

    /** Tipo de evento que atiende. */
    public static final String EVENT_TYPE = "route_finished_event";

    private final AppliedRouteRepository routes;

    /**
     * Crea el consumidor.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial de estados
     * @param outbox registro de eventos en la misma transacción
     * @param clock reloj en GMT-5
     * @param routes rutas ya terminadas y sumadas
     */
    public RouteFinishedHandler(VehicleRepository vehicles, StatusHistoryRepository history, EventOutbox outbox,
                                Clock clock, AppliedRouteRepository routes) {
        super(vehicles, history, outbox, clock);
        this.routes = routes;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public boolean handle(IncomingEvent event) {
        UUID routeId = EventData.uuid(event.data(), "route_id");
        UUID vehicleId = EventData.uuid(event.data(), "vehicle_id");
        BigDecimal distanceKm = EventData.nonNegativeDecimal(event.data(), "distance_km");
        Optional<Vehicle> found = anyVehicle(vehicleId);
        if (found.isEmpty()) {
            return false;
        }
        OffsetDateTime now = now();
        if (!routes.markApplied(routeId, vehicleId, distanceKm, now)) {
            return false;
        }
        Vehicle vehicle = found.get();
        VehicleStatus previous = vehicle.status();
        BigDecimal previousOdometer = vehicle.odometerKm();
        vehicle.finishRoute(routeId, distanceKm, now);
        Vehicle saved = save(vehicle);
        publish(VehicleEvents.ODOMETER_UPDATED, saved,
                VehicleEvents.odometerUpdated(saved, routeId, previousOdometer, distanceKm));
        if (saved.status() != previous) {
            recordStatusChange(saved, previous, StatusChangeCause.ROUTE_FINISHED, RouteStartedHandler.ROUTE_CAUSE,
                    VehicleEvents.ROUTE_FINISHED, routeId.toString(), now);
        }
        return true;
    }
}
