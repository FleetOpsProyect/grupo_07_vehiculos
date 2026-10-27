package co.edu.univalle.fleetops.vehicles.application.result;

import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Respuesta de DELETE /vehicles/{id}.
 *
 * @param vehicleId identificador
 * @param plate placa
 * @param status estado, siempre DELETED
 * @param deleted siempre {@code true}
 * @param updatedAt fecha de la baja
 */
public record DeletedVehicleResult(UUID vehicleId, String plate, VehicleStatus status, boolean deleted,
                                   OffsetDateTime updatedAt) {

    /**
     * Convierte un vehículo eliminado del dominio.
     *
     * @param vehicle vehículo del dominio
     * @return el resultado equivalente
     */
    public static DeletedVehicleResult from(Vehicle vehicle) {
        return new DeletedVehicleResult(vehicle.id(), vehicle.plate(), vehicle.status(), vehicle.isDeleted(),
                vehicle.updatedAt());
    }
}
