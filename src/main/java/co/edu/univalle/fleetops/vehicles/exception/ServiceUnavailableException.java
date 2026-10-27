package co.edu.univalle.fleetops.vehicles.exception;

import org.springframework.http.HttpStatus;

/** Un servicio del que dependemos no respondió: HTTP 503. */
public class ServiceUnavailableException extends ApiException {

    /**
     * Crea la excepción.
     * @param code código de error, ej. DOCUMENT_SERVICE_UNAVAILABLE
     * @param message mensaje descriptivo
     */
    public ServiceUnavailableException(final String code, final String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
