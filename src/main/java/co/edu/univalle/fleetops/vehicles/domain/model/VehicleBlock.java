package co.edu.univalle.fleetops.vehicles.domain.model;

import java.time.OffsetDateTime;

/**
 * Bloqueo activo de un vehículo. La clave es determinista ({@code TIPO:source_id}) para que un
 * evento repetido no cree dos bloqueos.
 *
 * @param blockKey clave única dentro del vehículo, por ejemplo {@code MAINTENANCE:<maintenance_id>}
 * @param type motivo del bloqueo
 * @param sourceId id del incidente, mantenimiento o tipo de documento que lo originó (texto)
 * @param reason detalle opcional del motivo
 * @param createdAt momento en que se agregó
 */
public record VehicleBlock(String blockKey, BlockType type, String sourceId, String reason,
                           OffsetDateTime createdAt) {

    /**
     * Crea un bloqueo derivando su clave del tipo y del origen.
     *
     * @param type motivo del bloqueo
     * @param sourceId id del origen del bloqueo
     * @param reason detalle opcional del motivo
     * @param createdAt momento en que se agrega
     * @return el bloqueo con su clave {@code TIPO:source_id}
     */
    public static VehicleBlock of(BlockType type, String sourceId, String reason, OffsetDateTime createdAt) {
        return new VehicleBlock(type + ":" + sourceId, type, sourceId, reason, createdAt);
    }
}
