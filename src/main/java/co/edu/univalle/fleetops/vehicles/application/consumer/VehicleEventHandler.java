package co.edu.univalle.fleetops.vehicles.application.consumer;

import co.edu.univalle.fleetops.vehicles.application.event.VehicleEvents;
import co.edu.univalle.fleetops.vehicles.application.port.in.IncomingEventHandler;
import co.edu.univalle.fleetops.vehicles.application.port.out.EventOutbox;
import co.edu.univalle.fleetops.vehicles.application.port.out.StatusHistoryRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusChangeCause;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusHistoryEntry;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Base de los consumidores que cambian un vehículo. Corren dentro de la transacción del consumo
 * (ProcessIncomingEventService): el cambio, el historial y los eventos publicados quedan juntos o
 * no queda nada. Los eventos de un vehículo desconocido o eliminado no tienen efecto.
 */
public abstract class VehicleEventHandler implements IncomingEventHandler {

    private final VehicleRepository vehicles;
    private final StatusHistoryRepository history;
    private final EventOutbox outbox;
    private final Clock clock;

    /**
     * Crea la base con los puertos comunes.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial de estados
     * @param outbox registro de eventos en la misma transacción
     * @param clock reloj en GMT-5
     */
    protected VehicleEventHandler(VehicleRepository vehicles, StatusHistoryRepository history, EventOutbox outbox,
                                  Clock clock) {
        this.vehicles = vehicles;
        this.history = history;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * Busca un vehículo que no esté eliminado.
     *
     * @param vehicleId id del vehículo
     * @return el vehículo, o vacío si no existe o está eliminado
     */
    protected Optional<Vehicle> activeVehicle(UUID vehicleId) {
        return vehicles.findById(vehicleId).filter(vehicle -> !vehicle.isDeleted());
    }

    /**
     * Busca un vehículo, aunque esté eliminado.
     *
     * @param vehicleId id del vehículo
     * @return el vehículo, o vacío si no existe
     */
    protected Optional<Vehicle> anyVehicle(UUID vehicleId) {
        return vehicles.findById(vehicleId);
    }

    /**
     * Momento actual en GMT-5.
     *
     * @return ahora
     */
    protected OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    /**
     * Guarda el vehículo sin publicar cambio de estado.
     *
     * @param vehicle vehículo modificado
     * @return el vehículo guardado
     */
    protected Vehicle save(Vehicle vehicle) {
        return vehicles.save(vehicle);
    }

    /**
     * Registra un evento del vehículo en el outbox.
     *
     * @param eventType nombre del evento
     * @param vehicle vehículo guardado
     * @param data contenido del evento
     */
    protected void publish(String eventType, Vehicle vehicle, Map<String, Object> data) {
        outbox.record(eventType, vehicle.id(), data);
    }

    /**
     * Guarda el vehículo, deja una fila en el historial y registra {@code vehicle_status_changed_event}.
     *
     * @param vehicle vehículo ya modificado
     * @param previous estado antes del cambio
     * @param cause causa para el historial
     * @param causeType tipo de la causa en el evento, por ejemplo {@code INCIDENT}
     * @param action acción en el evento, por ejemplo {@link VehicleEvents#BLOCK_ADDED}
     * @param sourceId id del incidente, mantenimiento, documento o ruta
     * @param now momento del cambio
     * @return el vehículo guardado
     */
    protected Vehicle saveStatusChange(Vehicle vehicle, VehicleStatus previous, StatusChangeCause cause,
                                       String causeType, String action, String sourceId, OffsetDateTime now) {
        Vehicle saved = vehicles.save(vehicle);
        recordStatusChange(saved, previous, cause, causeType, action, sourceId, now);
        return saved;
    }

    /**
     * Deja la fila del historial y registra {@code vehicle_status_changed_event} de un vehículo ya guardado.
     *
     * @param saved vehículo guardado
     * @param previous estado antes del cambio
     * @param cause causa para el historial
     * @param causeType tipo de la causa en el evento
     * @param action acción en el evento
     * @param sourceId id que originó el cambio
     * @param now momento del cambio
     */
    protected void recordStatusChange(Vehicle saved, VehicleStatus previous, StatusChangeCause cause,
                                      String causeType, String action, String sourceId, OffsetDateTime now) {
        history.save(StatusHistoryEntry.of(saved.id(), previous, saved.status(), cause, sourceId, now));
        outbox.record(VehicleEvents.STATUS_CHANGED, saved.id(),
                VehicleEvents.statusChanged(saved, previous, causeType, action, sourceId));
    }
}
