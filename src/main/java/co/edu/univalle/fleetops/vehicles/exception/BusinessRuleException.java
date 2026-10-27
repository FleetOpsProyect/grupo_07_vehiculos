package co.edu.univalle.fleetops.vehicles.exception;

import org.springframework.http.HttpStatus;

/** Datos válidos que violan una regla de negocio: HTTP 422. */
public class BusinessRuleException extends ApiException {

    /**
     * Crea la excepción.
     * @param code código de error, ej. STATUS_NOT_ALLOWED
     * @param message mensaje descriptivo
     */
    public BusinessRuleException(final String code, final String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
