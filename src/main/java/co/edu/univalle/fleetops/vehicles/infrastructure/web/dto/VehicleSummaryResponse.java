package co.edu.univalle.fleetops.vehicles.infrastructure.web.dto;

import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fila del listado GET /vehicles, en snake_case. Para el detalle completo se usa GET /vehicles/{id}.
 *
 * @param vehicleId {@code vehicle_id}
 * @param plate placa normalizada
 * @param vehicleType {@code vehicle_type}
 * @param brand marca
 * @param model modelo
 * @param year año de fabricación
 * @param odometerKm {@code odometer_km}
 * @param status estado actual
 */
public record VehicleSummaryResponse(UUID vehicleId, String plate, VehicleType vehicleType, String brand,
                                     String model, Integer year, BigDecimal odometerKm, VehicleStatus status) {
}