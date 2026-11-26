package co.edu.univalle.fleetops.vehicles.application.usecase;

import co.edu.univalle.fleetops.vehicles.application.command.ChangeToMaintenanceCommand;
import co.edu.univalle.fleetops.vehicles.application.event.VehicleEvents;
import co.edu.univalle.fleetops.vehicles.application.exception.ConcurrentVehicleUpdateException;
import co.edu.univalle.fleetops.vehicles.application.port.in.ChangeToMaintenanceUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.out.EventOutbox;
import co.edu.univalle.fleetops.vehicles.application.port.out.StatusHistoryRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.TransactionPort;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleResult;
import co.edu.univalle.fleetops.vehicles.domain.exception.StatusNotAllowedException;
import co.edu.univalle.fleetops.vehicles.domain.exception.VehicleNotFoundException;
import co.edu.univalle.fleetops.vehicles.domain.model.BlockType;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusChangeCause;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusHistoryEntry;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Paso a IN_MAINTENANCE desde ACTIVE; un vehículo eliminado responde 404. Repetir el mismo
 * {@code maintenance_id} devuelve el vehículo sin cambios ni registro nuevo en el historial. Si otra
 * petición modifica el vehículo al mismo tiempo, reintenta una sola vez en una transacción nueva: al
 * recargar se aplican las reglas normales (por ejemplo, el mismo {@code maintenance_id} ya aplicado
 * responde 200). Si el reintento también choca, el conflicto sube sin más intentos. Cada cambio real
 * registra {@code vehicle_status_changed_event}.
 */
public class ChangeToMaintenanceService implements ChangeToMaintenanceUseCase {

    private final VehicleRepository vehicles;
    private final StatusHistoryRepository history;
    private final EventOutbox outbox;
    private final TransactionPort transaction;
    private final Clock clock;

    /**
     * Crea el caso de uso con sus puertos.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial de estados
     * @param outbox registro de eventos en la misma transacción
     * @param transaction ejecución transaccional
     * @param clock reloj en GMT-5
     */
    public ChangeToMaintenanceService(VehicleRepository vehicles, StatusHistoryRepository history,
                                      EventOutbox outbox, TransactionPort transaction, Clock clock) {
        this.vehicles = vehicles;
        this.history = history;
        this.outbox = outbox;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public VehicleResult execute(ChangeToMaintenanceCommand command) {
        try {
            return attempt(command);
        } catch (ConcurrentVehicleUpdateException conflict) {
            return attempt(command);
        }
    }

    private VehicleResult attempt(ChangeToMaintenanceCommand command) {
        return transaction.execute(() -> {
            Vehicle vehicle = vehicles.findById(command.vehicleId())
                    .filter(found -> !found.isDeleted())
                    .orElseThrow(() -> new VehicleNotFoundException(command.vehicleId()));
            if (command.status() != VehicleStatus.IN_MAINTENANCE) {
                throw new StatusNotAllowedException();
            }
            VehicleStatus previous = vehicle.status();
            OffsetDateTime now = OffsetDateTime.now(clock);
            if (!vehicle.startMaintenance(command.maintenanceId(), now)) {
                return VehicleResult.from(vehicle);
            }
            Vehicle saved = vehicles.save(vehicle);
            history.save(StatusHistoryEntry.of(saved.id(), previous, saved.status(),
                    StatusChangeCause.MAINTENANCE_STARTED, command.maintenanceId().toString(), now));
            outbox.record(VehicleEvents.STATUS_CHANGED, saved.id(), VehicleEvents.statusChanged(saved, previous,
                    BlockType.MAINTENANCE.name(), VehicleEvents.BLOCK_ADDED, command.maintenanceId().toString()));
            return VehicleResult.from(saved);
        });
    }
}
