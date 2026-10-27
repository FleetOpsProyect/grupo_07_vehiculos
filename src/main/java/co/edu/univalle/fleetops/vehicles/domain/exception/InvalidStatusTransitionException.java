package co.edu.univalle.fleetops.vehicles.domain.exception;

import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;

/** Solo se pasa a mantenimiento desde ACTIVE (409 {@code INVALID_STATUS_TRANSITION}). */
public class InvalidStatusTransitionException extends DomainException {

    /**
     * Crea el error indicando el estado en que estaba el vehículo.
     *
     * @param currentStatus estado actual, distinto de ACTIVE
     */
    public InvalidStatusTransitionException(VehicleStatus currentStatus) {
        super("INVALID_STATUS_TRANSITION",
                "The vehicle is " + currentStatus + "; it can only go to maintenance from ACTIVE");
    }
}
