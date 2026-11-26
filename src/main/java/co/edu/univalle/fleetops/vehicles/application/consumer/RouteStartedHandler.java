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
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code route_started_event} (Telemetría, G6): marca la ruta en curso; el vehículo pasa a ON_ROUTE si no tiene
 * bloqueos. Si la ruta ya terminó (el fin llegó antes que el inicio), no hace nada. Solo publica
 * {@code vehicle_status_changed_event} si el estado cambia.
 */
public class RouteStartedHandler extends VehicleEventHandler {

    /** Tipo de evento que atiende. */
    public static final String EVENT_TYPE = "route_started_event";
    /** Tipo de causa de los cambios por rutas. */
    public static final String ROUTE_CAUSE = "ROUTE";

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
    public RouteStartedHandler(VehicleRepository vehicles, StatusHistoryRepository history, EventOutbox outbox,
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
        Optional<Vehicle> found = activeVehicle(vehicleId);
        if (found.isEmpty() || routes.isApplied(routeId)) {
            return false;
        }
        Vehicle vehicle = found.get();
        VehicleStatus previous = vehicle.status();
        OffsetDateTime now = now();
        if (!vehicle.startRoute(routeId, now)) {
            return false;
        }
        Vehicle saved = save(vehicle);
        if (saved.status() != previous) {
            recordStatusChange(saved, previous, StatusChangeCause.ROUTE_STARTED, ROUTE_CAUSE,
                    VehicleEvents.ROUTE_STARTED, routeId.toString(), now);
        }
        return true;
    }
}
