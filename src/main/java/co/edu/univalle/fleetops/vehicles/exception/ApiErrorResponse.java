package co.edu.univalle.fleetops.vehicles.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Formato estándar de error de FleetOps.
 * @param status código HTTP
 * @param code código de error estable; las pruebas verifican este campo
 * @param message mensaje orientativo
 * @param path ruta que originó el error
 * @param timestamp fecha y hora en GMT-5
 * @param errors errores por campo, solo en respuestas 400
 */
public record ApiErrorResponse(
        int status,
        String code,
        String message,
        String path,
        OffsetDateTime timestamp,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldErrorDetail> errors) {
}
