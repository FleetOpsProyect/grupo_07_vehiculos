package co.edu.univalle.fleetops.vehicles.dto;

import co.edu.univalle.fleetops.vehicles.model.VehicleStatus;
import java.util.List;
import java.util.Map;

/**
 * Respuesta paginada con bloque resumen (convención del catálogo FleetOps).
 * @param content elementos de la página
 * @param page número de página, desde 0
 * @param size tamaño de página
 * @param total total de elementos que cumplen el filtro
 * @param summary conteo de vehículos no eliminados por estado, para Reportes
 * @param <T> tipo de elemento
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long total,
        Map<VehicleStatus, Long> summary) {
}
