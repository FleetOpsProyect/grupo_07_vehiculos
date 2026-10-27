package co.edu.univalle.fleetops.vehicles.application.exception;

import co.edu.univalle.fleetops.vehicles.domain.exception.DomainException;

/** Documentación no respondió en 2 s; nunca se devuelve una lista sin validar (503). */
public class DocumentServiceUnavailableException extends DomainException {

    /**
     * Crea el error con la falla original del cliente HTTP.
     *
     * @param cause error de red, tiempo agotado o respuesta inválida
     */
    public DocumentServiceUnavailableException(Throwable cause) {
        super("DOCUMENT_SERVICE_UNAVAILABLE", "Document validity could not be checked; try again later", cause);
    }
}
