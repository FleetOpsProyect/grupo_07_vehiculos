package co.edu.univalle.fleetops.vehicles.exception;

import org.springframework.http.HttpStatus;

/** El estado actual no permite la operación (placa duplicada, asignaciones activas...): HTTP 409. */
public class BusinessException extends ApiException {

    /**
     * Crea la excepción.
     * @param code código de error, ej. PLATE_ALREADY_EXISTS
     * @param message mensaje descriptivo
     */
    public BusinessException(final String code, final String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
