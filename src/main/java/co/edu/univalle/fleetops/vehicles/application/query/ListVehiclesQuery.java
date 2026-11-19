package co.edu.univalle.fleetops.vehicles.application.query;

import co.edu.univalle.fleetops.vehicles.domain.exception.FieldError;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import java.util.List;

/**
 * Filtros de GET /vehicles paginado.
 *
 * @param status filtro opcional por estado
 * @param type filtro opcional por tipo
 * @param page página desde 0
 * @param size tamaño de página, de 1 a 100
 * @param preValidationErrors errores detectados en la capa de entrada (como un valor que no es del catálogo)
 */
public record ListVehiclesQuery(
        VehicleStatus status,
        VehicleType type,
        int page,
        int size,
        List<FieldError> preValidationErrors) {

    /**
     * Crea la consulta sin errores previos de validación.
     *
     * @param status filtro opcional por estado
     * @param type filtro opcional por tipo
     * @param page página desde 0
     * @param size tamaño de página, de 1 a 100
     */
    public ListVehiclesQuery(VehicleStatus status, VehicleType type, int page, int size) {
        this(status, type, page, size, List.of());
    }
}