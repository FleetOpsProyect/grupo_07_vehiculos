package co.edu.univalle.fleetops.vehicles.exception;

/**
 * Error de un campo en una respuesta 400.
 * @param field nombre del campo en snake_case
 * @param code código del error, ej. REQUIRED, OUT_OF_RANGE, NOT_EDITABLE
 */
public record FieldErrorDetail(String field, String code) {
}
