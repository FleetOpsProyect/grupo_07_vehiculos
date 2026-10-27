package co.edu.univalle.fleetops.vehicles.infrastructure.persistence.mapper;

import co.edu.univalle.fleetops.vehicles.infrastructure.persistence.entity.StatusHistoryEntity;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusHistoryEntry;
import org.springframework.stereotype.Component;

/** Convierte un registro del historial del dominio en su fila JPA. */
@Component
public class StatusHistoryMapper {

    /**
     * Crea la fila a partir del registro del dominio.
     *
     * @param entry registro del dominio
     * @return la fila lista para guardar
     */
    public StatusHistoryEntity toEntity(StatusHistoryEntry entry) {
        StatusHistoryEntity entity = new StatusHistoryEntity();
        entity.setId(entry.id());
        entity.setVehicleId(entry.vehicleId());
        entity.setFromStatus(entry.fromStatus());
        entity.setToStatus(entry.toStatus());
        entity.setCause(entry.cause());
        entity.setSourceId(entry.sourceId());
        entity.setChangedAt(entry.changedAt());
        return entity;
    }
}
