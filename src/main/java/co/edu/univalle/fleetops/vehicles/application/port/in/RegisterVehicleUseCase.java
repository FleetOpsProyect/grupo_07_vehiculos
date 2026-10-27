package co.edu.univalle.fleetops.vehicles.application.port.in;

import co.edu.univalle.fleetops.vehicles.application.command.RegisterVehicleCommand;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleResult;

/** Alta de un vehículo (POST /vehicles). */
public interface RegisterVehicleUseCase {

    /**
     * Ejecuta el caso de uso.
     *
     * @param command datos del alta
     * @return el vehículo creado en ACTIVE
     */
    VehicleResult execute(RegisterVehicleCommand command);
}
