package co.edu.univalle.fleetops.vehicles.dto;

import co.edu.univalle.fleetops.vehicles.model.VehicleStatus;
import java.util.UUID;

/**
 * Cuerpo de PUT /vehicles/{id}/status, que usa el scheduler de Mantenimiento (G3).
 * @param status solo se acepta IN_MAINTENANCE
 * @param maintenanceId orden de mantenimiento que origina el bloqueo
 * @param reason motivo (opcional)
 */
public record ChangeStatusRequest(VehicleStatus status, UUID maintenanceId, String reason) {
}
