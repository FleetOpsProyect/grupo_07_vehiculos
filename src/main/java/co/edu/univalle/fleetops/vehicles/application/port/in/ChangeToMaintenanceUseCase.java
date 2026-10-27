package co.edu.univalle.fleetops.vehicles.application.port.in;

import co.edu.univalle.fleetops.vehicles.application.command.ChangeToMaintenanceCommand;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleResult;

/** Paso a mantenimiento (PUT /vehicles/{id}/status). */
public interface ChangeToMaintenanceUseCase {

    /**
     * Ejecuta el caso de uso.
     *
     * @param command vehículo y mantenimiento
     * @return el vehículo en IN_MAINTENANCE
     */
    VehicleResult execute(ChangeToMaintenanceCommand command);
}
