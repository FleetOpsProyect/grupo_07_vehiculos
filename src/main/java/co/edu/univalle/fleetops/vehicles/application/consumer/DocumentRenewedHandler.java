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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code document_renewed_event} (Documentación, G1): si el dueño es un vehículo, quita
 * {@code DOCUMENT:<previous_document_id>}, el bloqueo que causó el documento anterior.
 */
public class DocumentRenewedHandler extends VehicleEventHandler {

    /** Tipo de evento que atiende. */
    public static final String EVENT_TYPE = "document_renewed_event";

    /**
     * Crea el consumidor.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial de estados
     * @param outbox registro de eventos en la misma transacción
     * @param clock reloj en GMT-5
     */
    public DocumentRenewedHandler(VehicleRepository vehicles, StatusHistoryRepository history, EventOutbox outbox,
                                  Clock clock) {
        super(vehicles, history, outbox, clock);
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public boolean handle(IncomingEvent event) {
        if (!DocumentExpiredHandler.VEHICLE_OWNER.equals(EventData.text(event.data(), "owner_type"))) {
            return false;
        }
        UUID vehicleId = EventData.uuid(event.data(), "owner_id");
        String previousDocumentId = EventData.text(event.data(), "previous_document_id");
        Optional<Vehicle> found = activeVehicle(vehicleId);
        if (found.isEmpty()) {
            return false;
        }
        Vehicle vehicle = found.get();
        VehicleStatus previous = vehicle.status();
        OffsetDateTime now = now();
        if (!vehicle.removeBlocks(List.of(BlockType.DOCUMENT + ":" + previousDocumentId), now)) {
            return false;
        }
        saveStatusChange(vehicle, previous, StatusChangeCause.DOCUMENT_RENEWED, BlockType.DOCUMENT.name(),
                VehicleEvents.BLOCK_REMOVED, previousDocumentId, now);
        return true;
    }
}
