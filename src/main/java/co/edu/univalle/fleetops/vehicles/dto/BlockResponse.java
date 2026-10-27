package co.edu.univalle.fleetops.vehicles.dto;

import co.edu.univalle.fleetops.vehicles.model.BlockType;
import co.edu.univalle.fleetops.vehicles.model.VehicleBlockModel;
import java.time.OffsetDateTime;

/**
 * Bloqueo activo tal como se expone en la API.
 * @param blockKey clave del bloqueo
 * @param type tipo de bloqueo
 * @param sourceId id del recurso que lo originó
 * @param reason motivo
 * @param createdAt fecha de creación
 */
public record BlockResponse(
        String blockKey,
        BlockType type,
        String sourceId,
        String reason,
        OffsetDateTime createdAt) {

    /**
     * Convierte la entidad JPA en la respuesta de la API.
     * @param block bloqueo
     * @return respuesta
     */
    public static BlockResponse from(final VehicleBlockModel block) {
        return new BlockResponse(block.getBlockKey(), block.getBlockType(), block.getSourceId(),
                block.getReason(), block.getCreatedAt());
    }
}
