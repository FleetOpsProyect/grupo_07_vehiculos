package co.edu.univalle.fleetops.vehicles.exception;

import org.springframework.http.HttpStatus;

/** Recurso inexistente: HTTP 404. */
public class ResourceNotFoundException extends ApiException {

    /**
     * Crea la excepción.
     * @param code código de error, ej. VEHICLE_NOT_FOUND
     * @param message mensaje descriptivo
     */
    public ResourceNotFoundException(final String code, final String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }
}
