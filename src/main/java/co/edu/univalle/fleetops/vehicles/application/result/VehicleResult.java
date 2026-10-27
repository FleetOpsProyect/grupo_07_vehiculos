package co.edu.univalle.fleetops.vehicles.application.result;

import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Vehículo completo, como lo devuelven POST, GET por id, PUT y PUT de estado.
 *
 * @param vehicleId identificador
 * @param plate placa normalizada
 * @param vehicleType tipo
 * @param brand marca
 * @param model modelo
 * @param year año de fabricación
 * @param color color
 * @param notes observaciones
 * @param odometerKm odómetro oficial
 * @param status estado actual
 * @param activeBlocks bloqueos activos
 * @param activeRouteId ruta en curso, o nulo
 * @param deleted baja lógica
 * @param createdAt fecha de creación
 * @param updatedAt fecha de la última modificación
 */
public record VehicleResult(UUID vehicleId, String plate, VehicleType vehicleType, String brand, String model,
                            Integer year, String color, String notes, BigDecimal odometerKm,
                            VehicleStatus status, List<BlockResult> activeBlocks, UUID activeRouteId,
                            boolean deleted, OffsetDateTime createdAt, OffsetDateTime updatedAt) {

    /**
     * Convierte un vehículo del dominio.
     *
     * @param vehicle vehículo del dominio
     * @return el resultado equivalente
     */
    public static VehicleResult from(Vehicle vehicle) {
        return new VehicleResult(vehicle.id(), vehicle.plate(), vehicle.type(), vehicle.details().brand(),
                vehicle.details().model(), vehicle.details().year(), vehicle.details().color(),
                vehicle.details().notes(), vehicle.odometerKm(), vehicle.status(),
                vehicle.blocks().stream().map(BlockResult::from).toList(), vehicle.activeRouteId(),
                vehicle.isDeleted(), vehicle.createdAt(), vehicle.updatedAt());
    }
}
