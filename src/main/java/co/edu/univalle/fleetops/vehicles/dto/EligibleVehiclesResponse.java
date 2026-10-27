package co.edu.univalle.fleetops.vehicles.dto;

import java.util.List;

/**
 * Respuesta de GET /vehicles?valid_until=...: aptos sin paginar (máximo 500).
 * @param content vehículos aptos
 * @param total cantidad total de aptos
 * @param truncated true si había más de 500 y se cortó la lista
 */
public record EligibleVehiclesResponse(List<VehicleResponse> content, int total, boolean truncated) {
}
