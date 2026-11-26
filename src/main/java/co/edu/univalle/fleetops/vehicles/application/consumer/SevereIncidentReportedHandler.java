package co.edu.univalle.fleetops.vehicles.application.consumer;

import co.edu.univalle.fleetops.vehicles.application.event.EventData;
import co.edu.univalle.fleetops.vehicles.application.event.IncomingEvent;
import co.edu.univalle.fleetops.vehicles.application.event.VehicleEvents;
import co.edu.univalle.fleetops.vehicles.application.port.out.EventOutbox;
import co.edu.univalle.fleetops.vehicles.application.port.out.StatusHistoryRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.domain.model.BlockType;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusChangeCause;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code severe_incident_reported_event} (Incidentes, G5): agrega el bloqueo {@code INCIDENT:<incident_id>} y
 * el vehículo pasa a BLOCKED. El incidente leve no se consume.
 */
public class SevereIncidentReportedHandler extends VehicleEventHandler {

    /** Tipo de evento que atiende. */
    public static final String EVENT_TYPE = "severe_incident_reported_event";

    /**
     * Crea el consumidor.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial de estados
     * @param outbox registro de eventos en la misma transacción
     * @param clock reloj en GMT-5
     */
    public SevereIncidentReportedHandler(VehicleRepository vehicles, StatusHistoryRepository history,
                                         EventOutbox outbox, Clock clock) {
        super(vehicles, history, outbox, clock);
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public boolean handle(IncomingEvent event) {
        UUID vehicleId = EventData.uuid(event.data(), "vehicle_id");
        String incidentId = EventData.text(event.data(), "incident_id");
        Optional<Vehicle> found = activeVehicle(vehicleId);
        if (found.isEmpty()) {
            return false;
        }
        Vehicle vehicle = found.get();
        VehicleStatus previous = vehicle.status();
        OffsetDateTime now = now();
        if (!vehicle.addBlock(BlockType.INCIDENT, incidentId, "SEVERE_INCIDENT", now)) {
            return false;
        }
        saveStatusChange(vehicle, previous, StatusChangeCause.INCIDENT_REPORTED, BlockType.INCIDENT.name(),
                VehicleEvents.BLOCK_ADDED, incidentId, now);
        return true;
    }
}
