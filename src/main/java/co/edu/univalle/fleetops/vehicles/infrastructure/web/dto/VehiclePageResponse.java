package co.edu.univalle.fleetops.vehicles.infrastructure.web.dto;

import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.util.List;
import java.util.Map;

/**
 * Página de GET /vehicles con el formato de paginación de las convenciones del curso.
 *
 * @param content vehículos de la página
 * @param page número de página desde 0
 * @param size tamaño de página
 * @param total total de vehículos que cumplen el filtro
 * @param summary conteo por estado de toda la flota (bloque de resumen para Reportes)
 */
public record VehiclePageResponse(List<VehicleSummaryResponse> content, int page, int size, long total,
                                  Map<VehicleStatus, Long> summary) {
}