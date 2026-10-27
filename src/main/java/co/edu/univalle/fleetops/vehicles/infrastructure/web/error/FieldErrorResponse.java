package co.edu.univalle.fleetops.vehicles.infrastructure.web.error;

/**
 * Error de un campo dentro de un 400.
 *
 * @param field nombre del campo en snake_case
 * @param code REQUIRED, INVALID_FORMAT, OUT_OF_RANGE, TOO_LONG o NOT_EDITABLE
 */
public record FieldErrorResponse(String field, String code) {
}
