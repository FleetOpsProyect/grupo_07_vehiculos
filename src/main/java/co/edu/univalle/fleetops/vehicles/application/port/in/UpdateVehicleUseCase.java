package co.edu.univalle.fleetops.vehicles.application.port.in;

import co.edu.univalle.fleetops.vehicles.application.command.UpdateVehicleCommand;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleResult;

/** Edición de datos operativos (PUT /vehicles/{id}). */
public interface UpdateVehicleUseCase {

    /**
     * Ejecuta el caso de uso.
     *
     * @param command nuevos datos
     * @return el vehículo actualizado
     */
    VehicleResult execute(UpdateVehicleCommand command);
}
