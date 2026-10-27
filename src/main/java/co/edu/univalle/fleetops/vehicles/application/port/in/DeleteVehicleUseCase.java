package co.edu.univalle.fleetops.vehicles.application.port.in;

import co.edu.univalle.fleetops.vehicles.application.result.DeletedVehicleResult;
import java.util.UUID;

/** Baja lógica (DELETE /vehicles/{id}). */
public interface DeleteVehicleUseCase {

    /**
     * Ejecuta el caso de uso.
     *
     * @param vehicleId id del vehículo
     * @return el vehículo marcado como DELETED
     */
    DeletedVehicleResult execute(UUID vehicleId);
}
