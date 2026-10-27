package co.edu.univalle.fleetops.vehicles.application.command;

import java.util.UUID;

/**
 * Datos de PUT /vehicles/{id}: reemplazan todos los campos editables.
 *
 * @param vehicleId vehículo a editar
 * @param brand marca
 * @param model modelo
 * @param year año de fabricación
 * @param color color opcional
 * @param notes observaciones opcionales
 */
public record UpdateVehicleCommand(UUID vehicleId, String brand, String model, Integer year, String color,
                                   String notes) {
}
