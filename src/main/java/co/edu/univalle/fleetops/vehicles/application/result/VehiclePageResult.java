package co.edu.univalle.fleetops.vehicles.application.result;

import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.util.List;
import java.util.Map;

/**
 * Respuesta de GET /vehicles paginado.
 *
 * @param content filas de la página
 * @param page número de página desde 0
 * @param size tamaño de página
 * @param total total de vehículos que cumplen el filtro
 * @param summary conteo por estado de toda la flota, sin contar eliminados
 */
public record VehiclePageResult(List<VehicleSummaryResult> content, int page, int size, long total,
                                Map<VehicleStatus, Long> summary) {
}
