package co.edu.univalle.fleetops.vehicles.application.usecase;

import co.edu.univalle.fleetops.vehicles.application.command.ChangeToMaintenanceCommand;
import co.edu.univalle.fleetops.vehicles.application.port.in.ChangeToMaintenanceUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.out.StatusHistoryRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.TransactionPort;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleResult;
import co.edu.univalle.fleetops.vehicles.domain.exception.StatusNotAllowedException;
import co.edu.univalle.fleetops.vehicles.domain.exception.VehicleNotFoundException;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusChangeCause;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusHistoryEntry;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Paso a IN_MAINTENANCE desde ACTIVE; un vehículo eliminado responde 404. Repetir el mismo
 * {@code maintenance_id} devuelve el vehículo sin cambios ni registro nuevo en el historial.
 */
public class ChangeToMaintenanceService implements ChangeToMaintenanceUseCase {

    private final VehicleRepository vehicles;
    private final StatusHistoryRepository history;
    private final TransactionPort transaction;
    private final Clock clock;

    /**
     * Crea el caso de uso con sus puertos.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial de estados
     * @param transaction ejecución transaccional
     * @param clock reloj en GMT-5
     */
    public ChangeToMaintenanceService(VehicleRepository vehicles, StatusHistoryRepository history,
                                      TransactionPort transaction, Clock clock) {
        this.vehicles = vehicles;
        this.history = history;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public VehicleResult execute(ChangeToMaintenanceCommand command) {
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
            return VehicleResult.from(saved);
        });
    }
}
