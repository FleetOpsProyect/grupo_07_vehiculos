package co.edu.univalle.fleetops.vehicles.application.query;

import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;

/**
 * Filtros de GET /vehicles paginado.
 *
 * @param status filtro opcional por estado
 * @param type filtro opcional por tipo
 * @param page página desde 0
 * @param size tamaño de página, de 1 a 100
 */
public record ListVehiclesQuery(VehicleStatus status, VehicleType type, int page, int size) {
}
