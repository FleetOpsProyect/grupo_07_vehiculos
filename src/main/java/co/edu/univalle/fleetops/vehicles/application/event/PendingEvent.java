package co.edu.univalle.fleetops.vehicles.application.event;

import java.util.UUID;

/**
 * Evento guardado en el outbox que todavía no se publicó.
 *
 * @param eventId identificador del evento
 * @param eventType nombre del evento, que viaja como atributo {@code event_type} del mensaje
 * @param payload sobre completo en JSON, tal como se guardó
 */
public record PendingEvent(UUID eventId, String eventType, String payload) {
}
