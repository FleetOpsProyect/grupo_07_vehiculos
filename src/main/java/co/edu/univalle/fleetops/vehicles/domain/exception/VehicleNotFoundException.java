package co.edu.univalle.fleetops.vehicles.domain.exception;

import java.util.UUID;

/** El vehículo no existe (404 {@code VEHICLE_NOT_FOUND}). */
public class VehicleNotFoundException extends DomainException {

    /**
     * Crea el error para un id inexistente.
     *
     * @param vehicleId id que no se encontró
     */
    public VehicleNotFoundException(UUID vehicleId) {
        super("VEHICLE_NOT_FOUND", "No vehicle exists with id " + vehicleId);
    }
}
