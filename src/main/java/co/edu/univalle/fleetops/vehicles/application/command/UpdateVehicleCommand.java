package co.edu.univalle.fleetops.vehicles.application.command;

import java.util.List;
import java.util.UUID;

import co.edu.univalle.fleetops.vehicles.domain.exception.FieldError;

/**
 * Datos de PUT /vehicles/{id}: reemplazan todos los campos editables.
 *
 * @param vehicleId vehículo a editar
 * @param brand marca
 * @param model modelo
 * @param year año de fabricación
 * @param color color opcional
 * @param notes observaciones opcionales
 * @param preValidationErrors errores detectados en la capa de entrada (como campos no editables)
 */
public record UpdateVehicleCommand(
        UUID vehicleId,
        String brand,
        String model,
        Integer year,
        String color,
        String notes,
        List<FieldError> preValidationErrors) {

    /** Normaliza la lista de errores previos para evitar nulos. */
    public UpdateVehicleCommand {
        preValidationErrors = preValidationErrors == null ? List.of() : List.copyOf(preValidationErrors);
    }

    /**
     * Crea el comando sin errores previos de validación.
     *
     * @param vehicleId vehículo a editar
     * @param brand marca
     * @param model modelo
     * @param year año de fabricación
     * @param color color opcional
     * @param notes observaciones opcionales
     */
    public UpdateVehicleCommand(
            UUID vehicleId,
            String brand,
            String model,
            Integer year,
            String color,
            String notes) {
        this(vehicleId, brand, model, year, color, notes, List.of());
    }
}