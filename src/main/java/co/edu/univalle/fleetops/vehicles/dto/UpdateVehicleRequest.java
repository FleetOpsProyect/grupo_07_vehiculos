package co.edu.univalle.fleetops.vehicles.dto;

import co.edu.univalle.fleetops.vehicles.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.model.VehicleType;
import java.math.BigDecimal;

/**
 * Cuerpo de PUT /vehicles/{id}. Reemplaza todos los campos editables.
 * Los campos no editables se declaran solo para responder 400 NOT_EDITABLE si llegan.
 * @param brand marca
 * @param model modelo
 * @param year año del modelo
 * @param color color (opcional)
 * @param notes observaciones (opcional)
 * @param plate no editable
 * @param vehicleType no editable
 * @param odometerKm no editable: solo cambia con las rutas
 * @param status no editable: se deriva de los bloqueos
 */
public record UpdateVehicleRequest(
        String brand,
        String model,
        Integer year,
        String color,
        String notes,
        String plate,
        VehicleType vehicleType,
        BigDecimal odometerKm,
        VehicleStatus status) {
}
