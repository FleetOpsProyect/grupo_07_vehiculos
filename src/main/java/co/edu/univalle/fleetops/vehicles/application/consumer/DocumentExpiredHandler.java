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
 * {@code document_expired_event} (Documentación, G1): si el dueño es un vehículo, agrega
 * {@code DOCUMENT:<document_id>} y el vehículo pasa a INACTIVE_EXPIRED_DOCUMENTS (si no tiene un bloqueo de
 * mayor prioridad). Los documentos de conductores se ignoran.
 */
public class DocumentExpiredHandler extends VehicleEventHandler {

    /** Tipo de evento que atiende. */
    public static final String EVENT_TYPE = "document_expired_event";
    /** Valor de {@code owner_type} de los documentos de vehículos. */
    public static final String VEHICLE_OWNER = "VEHICLE";

    /**
     * Crea el consumidor.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial de estados
     * @param outbox registro de eventos en la misma transacción
     * @param clock reloj en GMT-5
     */
    public DocumentExpiredHandler(VehicleRepository vehicles, StatusHistoryRepository history, EventOutbox outbox,
                                  Clock clock) {
        super(vehicles, history, outbox, clock);
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public boolean handle(IncomingEvent event) {
        if (!VEHICLE_OWNER.equals(EventData.text(event.data(), "owner_type"))) {
            return false;
        }
        UUID vehicleId = EventData.uuid(event.data(), "owner_id");
        String documentId = EventData.text(event.data(), "document_id");
        String documentType = EventData.optionalText(event.data(), "document_type");
        Optional<Vehicle> found = activeVehicle(vehicleId);
        if (found.isEmpty()) {
            return false;
        }
        Vehicle vehicle = found.get();
        VehicleStatus previous = vehicle.status();
        OffsetDateTime now = now();
        if (!vehicle.addBlock(BlockType.DOCUMENT, documentId, documentType, now)) {
            return false;
        }
        saveStatusChange(vehicle, previous, StatusChangeCause.DOCUMENT_EXPIRED, BlockType.DOCUMENT.name(),
                VehicleEvents.BLOCK_ADDED, documentId, now);
        return true;
    }
}
