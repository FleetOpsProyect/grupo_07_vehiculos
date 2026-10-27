package co.edu.univalle.fleetops.vehicles.dto;

import co.edu.univalle.fleetops.vehicles.model.VehicleBlockModel;
import co.edu.univalle.fleetops.vehicles.model.VehicleModel;
import co.edu.univalle.fleetops.vehicles.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.model.VehicleType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Vehículo tal como se expone en la API.
 * @param vehicleId id del vehículo
 * @param plate placa
 * @param vehicleType tipo
 * @param brand marca
 * @param model modelo
 * @param year año
 * @param color color
 * @param notes observaciones
 * @param odometerKm kilometraje oficial
 * @param status estado derivado
 * @param activeBlocks bloqueos vigentes
 * @param activeRouteId ruta en curso
 * @param deleted baja lógica
 * @param version versión del registro
 * @param createdAt fecha de registro
 * @param updatedAt fecha de última modificación
 */
public record VehicleResponse(
        UUID vehicleId,
        String plate,
        VehicleType vehicleType,
        String brand,
        String model,
        Integer year,
        String color,
        String notes,
        BigDecimal odometerKm,
        VehicleStatus status,
        List<BlockResponse> activeBlocks,
        UUID activeRouteId,
        boolean deleted,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    /**
     * Convierte la entidad JPA en la respuesta de la API.
     * Debe llamarse dentro de la transacción porque recorre los bloqueos (LAZY).
     * @param vehicle vehículo
     * @return respuesta
     */
    public static VehicleResponse from(final VehicleModel vehicle) {
        List<BlockResponse> blocks = vehicle.getBlocks().stream()
                .sorted(Comparator.comparing(VehicleBlockModel::getBlockKey))
                .map(BlockResponse::from)
                .toList();
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getPlate(),
                vehicle.getVehicleType(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getYear(),
                vehicle.getColor(),
                vehicle.getNotes(),
                vehicle.getOdometerKm(),
                vehicle.getStatus(),
                blocks,
                vehicle.getActiveRouteId(),
                vehicle.isDeleted(),
                vehicle.getVersion(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt());
    }
}
