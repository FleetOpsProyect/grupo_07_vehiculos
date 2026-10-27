package co.edu.univalle.fleetops.vehicles.dto;

import co.edu.univalle.fleetops.vehicles.model.VehicleType;
import java.math.BigDecimal;

/**
 * Cuerpo de POST /vehicles. En JSON los campos van en snake_case
 * (vehicle_type, odometer_km).
 * @param plate placa; se normaliza a mayúsculas sin espacios ni guiones
 * @param vehicleType tipo de vehículo
 * @param brand marca
 * @param model modelo
 * @param year año del modelo
 * @param color color (opcional)
 * @param notes observaciones (opcional)
 * @param odometerKm kilometraje inicial
 */
public record CreateVehicleRequest(
        String plate,
        VehicleType vehicleType,
        String brand,
        String model,
        Integer year,
        String color,
        String notes,
        BigDecimal odometerKm) {
}
