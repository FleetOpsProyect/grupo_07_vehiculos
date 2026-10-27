package co.edu.univalle.fleetops.vehicles.infrastructure.web.dto;

import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import java.math.BigDecimal;

/**
 * Cuerpo de POST /vehicles (snake_case por la configuración global de Jackson).
 *
 * @param plate placa, por ejemplo {@code abc-123}
 * @param vehicleType {@code vehicle_type}: CAR, PICKUP_TRUCK o SPECIALIZED_VEHICLE
 * @param brand marca
 * @param model modelo
 * @param year año de fabricación
 * @param color color opcional
 * @param notes observaciones opcionales
 * @param odometerKm {@code odometer_km}: odómetro inicial
 */
public record CreateVehicleRequest(String plate, VehicleType vehicleType, String brand, String model,
                                   Integer year, String color, String notes, BigDecimal odometerKm) {
}
