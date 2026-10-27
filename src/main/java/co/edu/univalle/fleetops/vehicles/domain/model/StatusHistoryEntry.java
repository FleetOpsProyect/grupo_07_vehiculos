package co.edu.univalle.fleetops.vehicles.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro de trazabilidad de un cambio de estado.
 *
 * @param id identificador del registro
 * @param vehicleId vehículo afectado
 * @param fromStatus estado anterior; nulo en el alta
 * @param toStatus estado nuevo
 * @param cause causa del cambio
 * @param sourceId id del origen del cambio (por ejemplo el mantenimiento), si lo hay
 * @param changedAt momento del cambio
 */
public record StatusHistoryEntry(UUID id, UUID vehicleId, VehicleStatus fromStatus, VehicleStatus toStatus,
                                 StatusChangeCause cause, String sourceId, OffsetDateTime changedAt) {

    /**
     * Crea un registro nuevo con id generado.
     *
     * @param vehicleId vehículo afectado
     * @param fromStatus estado anterior; nulo en el alta
     * @param toStatus estado nuevo
     * @param cause causa del cambio
     * @param sourceId id del origen del cambio, si lo hay
     * @param changedAt momento del cambio
     * @return el registro listo para guardar
     */
    public static StatusHistoryEntry of(UUID vehicleId, VehicleStatus fromStatus, VehicleStatus toStatus,
                                        StatusChangeCause cause, String sourceId, OffsetDateTime changedAt) {
        return new StatusHistoryEntry(UUID.randomUUID(), vehicleId, fromStatus, toStatus, cause, sourceId,
                changedAt);
    }
}
