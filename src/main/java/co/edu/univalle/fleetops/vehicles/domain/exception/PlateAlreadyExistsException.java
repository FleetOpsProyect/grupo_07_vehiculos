package co.edu.univalle.fleetops.vehicles.domain.exception;

/** Ya existe un vehículo no eliminado con esa placa (409 {@code PLATE_ALREADY_EXISTS}). */
public class PlateAlreadyExistsException extends DomainException {

    /**
     * Crea el error para una placa repetida.
     *
     * @param plate placa normalizada
     */
    public PlateAlreadyExistsException(String plate) {
        super("PLATE_ALREADY_EXISTS", "A vehicle with plate " + plate + " already exists");
    }
}
