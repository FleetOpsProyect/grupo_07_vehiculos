package co.edu.univalle.fleetops.vehicles.domain.exception;

/**
 * Error de un campo dentro de un {@code VALIDATION_ERROR}.
 *
 * @param field nombre del campo en snake_case
 * @param code {@code REQUIRED}, {@code INVALID_FORMAT}, {@code OUT_OF_RANGE}, {@code TOO_LONG} o
 *     {@code NOT_EDITABLE}
 */
public record FieldError(String field, String code) {
}
