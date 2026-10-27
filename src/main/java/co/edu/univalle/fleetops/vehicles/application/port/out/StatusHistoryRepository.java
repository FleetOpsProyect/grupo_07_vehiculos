package co.edu.univalle.fleetops.vehicles.application.port.out;

import co.edu.univalle.fleetops.vehicles.domain.model.StatusHistoryEntry;

/** Persistencia del historial de cambios de estado. */
public interface StatusHistoryRepository {

    /**
     * Guarda un registro del historial.
     *
     * @param entry registro a guardar
     */
    void save(StatusHistoryEntry entry);
}
