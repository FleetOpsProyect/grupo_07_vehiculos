package co.edu.univalle.fleetops.vehicles.infrastructure.web.dto;

/**
 * Cuerpo de PUT /vehicles/{id}/status (snake_case por la configuración global de Jackson). Los dos
 * campos llegan como texto para que el mapper pueda reportar todos los errores a la vez.
 *
 * @param status estado pedido; solo se acepta {@code IN_MAINTENANCE}
 * @param maintenanceId {@code maintenance_id}: UUID del mantenimiento que origina el bloqueo
 */
public record ChangeStatusRequest(String status, String maintenanceId) {
}
