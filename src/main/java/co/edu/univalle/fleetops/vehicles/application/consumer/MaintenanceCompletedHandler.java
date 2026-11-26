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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code maintenance_completed_event} (Mantenimiento, G3): quita {@code MAINTENANCE:<maintenance_order_id>} y,
 * si trae {@code incident_id} (mantenimiento correctivo), también {@code INCIDENT:<incident_id>}. El vehículo
 * vuelve a ACTIVE solo si no le queda ningún bloqueo.
 */
public class MaintenanceCompletedHandler extends VehicleEventHandler {

    /** Tipo de evento que atiende. */
    public static final String EVENT_TYPE = "maintenance_completed_event";

    /**
     * Crea el consumidor.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial de estados
     * @param outbox registro de eventos en la misma transacción
     * @param clock reloj en GMT-5
     */
    public MaintenanceCompletedHandler(VehicleRepository vehicles, StatusHistoryRepository history,
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
        String maintenanceOrderId = EventData.text(event.data(), "maintenance_order_id");
        String incidentId = EventData.optionalText(event.data(), "incident_id");
        Optional<Vehicle> found = activeVehicle(vehicleId);
        if (found.isEmpty()) {
            return false;
        }
        List<String> keys = new ArrayList<>();
        keys.add(BlockType.MAINTENANCE + ":" + maintenanceOrderId);
        if (incidentId != null) {
            keys.add(BlockType.INCIDENT + ":" + incidentId);
        }
        Vehicle vehicle = found.get();
        VehicleStatus previous = vehicle.status();
        OffsetDateTime now = now();
        if (!vehicle.removeBlocks(keys, now)) {
            return false;
        }
        saveStatusChange(vehicle, previous, StatusChangeCause.MAINTENANCE_COMPLETED, BlockType.MAINTENANCE.name(),
                VehicleEvents.BLOCK_REMOVED, maintenanceOrderId, now);
        return true;
    }
}
