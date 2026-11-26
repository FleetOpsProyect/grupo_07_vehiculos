package co.edu.univalle.fleetops.vehicles.application.port.out;

import co.edu.univalle.fleetops.vehicles.application.event.PendingEvent;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Cola de eventos del outbox que el publicador recorre. */
public interface OutboxQueue {

    /**
     * Toma y bloquea los eventos PENDING más antiguos; otro publicador no los ve mientras dure la
     * transacción.
     *
     * @param limit cantidad máxima de eventos
     * @return los eventos, del más antiguo al más nuevo
     */
    List<PendingEvent> lockPending(int limit);

    /**
     * Marca un evento como publicado.
     *
     * @param eventId evento publicado
     * @param publishedAt momento de la publicación
     */
    void markPublished(UUID eventId, OffsetDateTime publishedAt);

    /**
     * Suma un intento fallido y guarda el error; el evento sigue PENDING.
     *
     * @param eventId evento que falló
     * @param error descripción del error
     */
    void markFailed(UUID eventId, String error);
}
