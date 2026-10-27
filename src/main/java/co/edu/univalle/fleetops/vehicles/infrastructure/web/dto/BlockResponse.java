package co.edu.univalle.fleetops.vehicles.infrastructure.web.dto;

import co.edu.univalle.fleetops.vehicles.domain.model.BlockType;
import java.time.OffsetDateTime;

/**
 * Bloqueo activo en la respuesta del vehículo.
 *
 * @param blockKey {@code block_key}
 * @param type motivo del bloqueo
 * @param sourceId {@code source_id}
 * @param reason detalle del motivo
 * @param createdAt {@code created_at} en GMT-5
 */
public record BlockResponse(String blockKey, BlockType type, String sourceId, String reason,
                            OffsetDateTime createdAt) {
}
