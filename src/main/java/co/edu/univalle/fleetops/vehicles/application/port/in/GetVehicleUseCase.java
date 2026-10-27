package co.edu.univalle.fleetops.vehicles.application.port.in;

import co.edu.univalle.fleetops.vehicles.application.result.VehicleResult;
import java.util.UUID;

/** Detalle de un vehículo (GET /vehicles/{id}). */
public interface GetVehicleUseCase {

    /**
     * Ejecuta el caso de uso.
     *
     * @param vehicleId id del vehículo
     * @return el vehículo con sus bloqueos
     */
    VehicleResult execute(UUID vehicleId);
}
