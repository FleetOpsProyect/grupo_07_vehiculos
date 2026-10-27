package co.edu.univalle.fleetops.vehicles.application.exception;

import co.edu.univalle.fleetops.vehicles.domain.exception.DomainException;

/** Asignaciones no respondió en 2 s; no se borra sin confirmar (503). */
public class AssignmentServiceUnavailableException extends DomainException {

    /**
     * Crea el error con la falla original del cliente HTTP.
     *
     * @param cause error de red, tiempo agotado o respuesta inválida
     */
    public AssignmentServiceUnavailableException(Throwable cause) {
        super("ASSIGNMENT_SERVICE_UNAVAILABLE", "Vehicle assignments could not be checked; try again later",
                cause);
    }
}
