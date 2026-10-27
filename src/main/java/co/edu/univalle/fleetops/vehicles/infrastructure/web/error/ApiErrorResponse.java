package co.edu.univalle.fleetops.vehicles.infrastructure.web.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Formato de error del contrato, sin campo {@code status}. {@code errors} solo viaja en los 400.
 *
 * @param code código estable, por ejemplo {@code VEHICLE_NOT_FOUND}
 * @param message texto orientativo
 * @param timestamp momento del error en GMT-5
 * @param path ruta pedida
 * @param errors errores por campo, solo en 400
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(String code, String message, OffsetDateTime timestamp, String path,
                               List<FieldErrorResponse> errors) {
}
