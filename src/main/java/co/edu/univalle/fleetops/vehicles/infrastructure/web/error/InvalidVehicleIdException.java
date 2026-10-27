package co.edu.univalle.fleetops.vehicles.infrastructure.web.error;

/**
 * El id de la ruta no es un UUID. Es un error de la capa REST: se responde 404
 * {@code VEHICLE_NOT_FOUND} sin llegar al dominio.
 */
public class InvalidVehicleIdException extends RuntimeException {

    /**
     * Crea el error con el id tal como llegó.
     *
     * @param rawId id de la ruta que no es un UUID
     */
    public InvalidVehicleIdException(String rawId) {
        super("No vehicle exists with id " + rawId);
    }
}
