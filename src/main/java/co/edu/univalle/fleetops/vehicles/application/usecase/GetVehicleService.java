package co.edu.univalle.fleetops.vehicles.application.usecase;

import co.edu.univalle.fleetops.vehicles.application.port.in.GetVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleResult;
import co.edu.univalle.fleetops.vehicles.domain.exception.VehicleNotFoundException;
import java.util.UUID;

/** Detalle de un vehículo, incluido uno eliminado (la respuesta trae {@code deleted}). */
public class GetVehicleService implements GetVehicleUseCase {

    private final VehicleRepository vehicles;

    /**
     * Crea el caso de uso con sus puertos.
     *
     * @param vehicles persistencia de vehículos
     */
    public GetVehicleService(VehicleRepository vehicles) {
        this.vehicles = vehicles;
    }

    @Override
    public VehicleResult execute(UUID vehicleId) {
        return vehicles.findById(vehicleId)
                .map(VehicleResult::from)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
    }
}
