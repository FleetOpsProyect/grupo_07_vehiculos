package co.edu.univalle.fleetops.vehicles.application.port.in;

import co.edu.univalle.fleetops.vehicles.application.result.PublishBatchResult;

/** Publica los eventos pendientes del outbox. */
public interface PublishPendingEventsUseCase {

    /**
     * Publica hasta {@code batchSize} eventos pendientes, del más antiguo al más nuevo. Un evento que
     * falla no detiene a los demás.
     *
     * @param batchSize cantidad máxima de eventos de esta vuelta
     * @return cuántos se publicaron y cuántos fallaron
     */
    PublishBatchResult execute(int batchSize);
}
