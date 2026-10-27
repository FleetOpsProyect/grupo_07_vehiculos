package co.edu.univalle.fleetops.vehicles.infrastructure.web.dto;

import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Vehículo completo (respuesta de POST /vehicles y GET /vehicles/{id}), en snake_case.
 *
 * @param vehicleId {@code vehicle_id}
 * @param plate placa normalizada
 * @param vehicleType {@code vehicle_type}
 * @param brand marca
 * @param model modelo
 * @param year año de fabricación
 * @param color color
 * @param notes observaciones
 * @param odometerKm {@code odometer_km}
 * @param status estado actual
 * @param activeBlocks {@code active_blocks}
 * @param activeRouteId {@code active_route_id}
 * @param deleted baja lógica
 * @param createdAt {@code created_at} en GMT-5
 * @param updatedAt {@code updated_at} en GMT-5
 */
public record VehicleResponse(UUID vehicleId, String plate, VehicleType vehicleType, String brand, String model,
                              Integer year, String color, String notes, BigDecimal odometerKm,
                              VehicleStatus status, List<BlockResponse> activeBlocks, UUID activeRouteId,
                              boolean deleted, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
