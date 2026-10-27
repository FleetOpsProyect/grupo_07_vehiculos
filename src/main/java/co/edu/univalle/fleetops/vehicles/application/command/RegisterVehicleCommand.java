package co.edu.univalle.fleetops.vehicles.application.command;

import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import java.math.BigDecimal;

/**
 * Datos de POST /vehicles tal como llegan, sin normalizar ni validar.
 *
 * @param plate placa, por ejemplo {@code abc-123}
 * @param vehicleType tipo de vehículo
 * @param brand marca
 * @param model modelo
 * @param year año de fabricación
 * @param color color opcional
 * @param notes observaciones opcionales
 * @param odometerKm odómetro inicial en kilómetros
 */
public record RegisterVehicleCommand(String plate, VehicleType vehicleType, String brand, String model,
                                     Integer year, String color, String notes, BigDecimal odometerKm) {
}
