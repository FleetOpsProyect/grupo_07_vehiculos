package co.edu.univalle.fleetops.vehicles.application.usecase;

import co.edu.univalle.fleetops.vehicles.application.event.VehicleEvents;
import co.edu.univalle.fleetops.vehicles.application.port.in.DeleteVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.out.ActiveAssignmentsPort;
import co.edu.univalle.fleetops.vehicles.application.port.out.EventOutbox;
import co.edu.univalle.fleetops.vehicles.application.port.out.StatusHistoryRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.TransactionPort;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.result.DeletedVehicleResult;
import co.edu.univalle.fleetops.vehicles.domain.exception.VehicleHasActiveAssignmentsException;
import co.edu.univalle.fleetops.vehicles.domain.exception.VehicleNotFoundException;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusChangeCause;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusHistoryEntry;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Baja lógica. Consulta a Asignaciones fuera de la transacción: si no responde, la excepción sube
 * y no se borra nada. Un vehículo ya eliminado responde igual, sin consultar ni publicar. La baja
 * registra {@code vehicle_deleted_event}.
 */
public class DeleteVehicleService implements DeleteVehicleUseCase {

    private final VehicleRepository vehicles;
    private final StatusHistoryRepository history;
    private final ActiveAssignmentsPort assignments;
    private final EventOutbox outbox;
    private final TransactionPort transaction;
    private final Clock clock;

    /**
     * Crea el caso de uso con sus puertos.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial de estados
     * @param assignments consulta de asignaciones activas a Asignaciones
     * @param outbox registro de eventos en la misma transacción
     * @param transaction ejecución transaccional
     * @param clock reloj en GMT-5
     */
    public DeleteVehicleService(VehicleRepository vehicles, StatusHistoryRepository history,
                                ActiveAssignmentsPort assignments, EventOutbox outbox, TransactionPort transaction,
                                Clock clock) {
        this.vehicles = vehicles;
        this.history = history;
        this.assignments = assignments;
        this.outbox = outbox;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public DeletedVehicleResult execute(UUID vehicleId) {
        Vehicle vehicle = vehicles.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
        if (vehicle.isDeleted()) {
            return DeletedVehicleResult.from(vehicle);
        }
        int active = assignments.countActiveAssignments(vehicleId);
        if (active > 0) {
            throw new VehicleHasActiveAssignmentsException(active);
        }
        return transaction.execute(() -> {
            VehicleStatus previous = vehicle.status();
            OffsetDateTime now = OffsetDateTime.now(clock);
            vehicle.markDeleted(now);
            Vehicle saved = vehicles.save(vehicle);
            history.save(StatusHistoryEntry.of(saved.id(), previous, saved.status(),
                    StatusChangeCause.VEHICLE_DELETED, null, now));
            outbox.record(VehicleEvents.DELETED, saved.id(), VehicleEvents.deleted(saved));
            return DeletedVehicleResult.from(saved);
        });
    }
}
