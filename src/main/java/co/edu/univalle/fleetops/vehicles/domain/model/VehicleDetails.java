package co.edu.univalle.fleetops.vehicles.domain.model;

/**
 * Datos operativos editables del vehículo (PUT /vehicles/{id}).
 *
 * @param brand marca, de 2 a 50 caracteres
 * @param model modelo, de 1 a 50 caracteres
 * @param year año de fabricación, entre 1980 y el año actual + 1
 * @param color color opcional, máximo 30 caracteres
 * @param notes observaciones opcionales, máximo 500 caracteres
 */
public record VehicleDetails(String brand, String model, Integer year, String color, String notes) {
}
