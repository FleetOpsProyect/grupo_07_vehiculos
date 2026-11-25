package co.edu.univalle.fleetops.vehicles.application.port.out;

import java.time.OffsetDateTime;

/** Registro de los mensajes recibidos que ya se aplicaron. */
public interface ProcessedEventRepository {

    /**
     * Registra el mensaje como procesado, dentro de la transacción en curso.
     *
     * @param eventId id del evento recibido
     * @param eventType nombre del evento
     * @param processedAt momento del registro
     * @return {@code true} si era nuevo; {@code false} si ya estaba registrado
     */
    boolean markProcessed(String eventId, String eventType, OffsetDateTime processedAt);
}
