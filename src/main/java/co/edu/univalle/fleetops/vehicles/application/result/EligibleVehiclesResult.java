package co.edu.univalle.fleetops.vehicles.application.result;

import java.util.List;

/**
 * Respuesta de GET /vehicles?valid_until=..., sin paginar.
 *
 * @param content vehículos aptos, hasta 500
 * @param total cantidad de vehículos aptos encontrados, antes de recortar
 * @param truncated {@code true} si había más de 500 y se recortó la lista
 */
public record EligibleVehiclesResult(List<VehicleSummaryResult> content, int total, boolean truncated) {
}
