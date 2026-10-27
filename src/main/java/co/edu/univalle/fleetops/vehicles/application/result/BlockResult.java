package co.edu.univalle.fleetops.vehicles.application.result;

import co.edu.univalle.fleetops.vehicles.domain.model.BlockType;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleBlock;
import java.time.OffsetDateTime;

/**
 * Bloqueo activo tal como se muestra en el detalle del vehículo.
 *
 * @param blockKey clave única del bloqueo
 * @param type motivo del bloqueo
 * @param sourceId id del origen
 * @param reason detalle del motivo
 * @param createdAt momento en que se agregó
 */
public record BlockResult(String blockKey, BlockType type, String sourceId, String reason,
                          OffsetDateTime createdAt) {

    /**
     * Convierte un bloqueo del dominio.
     *
     * @param block bloqueo del dominio
     * @return el resultado equivalente
     */
    public static BlockResult from(VehicleBlock block) {
        return new BlockResult(block.blockKey(), block.type(), block.sourceId(), block.reason(),
                block.createdAt());
    }
}
