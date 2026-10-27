package co.edu.univalle.fleetops.vehicles.application.result;

import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fila de los listados de GET /vehicles.
 *
 * @param vehicleId identificador
 * @param plate placa
 * @param vehicleType tipo
 * @param brand marca
 * @param model modelo
 * @param year año de fabricación
 * @param odometerKm odómetro oficial
 * @param status estado actual
 */
public record VehicleSummaryResult(UUID vehicleId, String plate, VehicleType vehicleType, String brand,
                                   String model, Integer year, BigDecimal odometerKm, VehicleStatus status) {

    /**
     * Convierte un vehículo del dominio.
     *
     * @param vehicle vehículo del dominio
     * @return la fila equivalente
     */
    public static VehicleSummaryResult from(Vehicle vehicle) {
        return new VehicleSummaryResult(vehicle.id(), vehicle.plate(), vehicle.type(), vehicle.details().brand(),
                vehicle.details().model(), vehicle.details().year(), vehicle.odometerKm(), vehicle.status());
    }
}
