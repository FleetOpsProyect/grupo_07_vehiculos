package co.edu.univalle.fleetops.vehicles.infrastructure.web.dto;

import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Respuesta de DELETE /vehicles/{id}, en snake_case (sección 3.6 del contrato).
 *
 * @param vehicleId {@code vehicle_id}
 * @param plate placa normalizada
 * @param status siempre DELETED
 * @param deleted siempre {@code true}
 * @param updatedAt {@code updated_at}: momento de la baja, en GMT-5
 */
public record DeletedVehicleResponse(UUID vehicleId, String plate, VehicleStatus status, boolean deleted,
                                     OffsetDateTime updatedAt) {
}
