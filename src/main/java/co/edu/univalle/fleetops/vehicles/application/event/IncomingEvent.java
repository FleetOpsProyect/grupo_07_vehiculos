package co.edu.univalle.fleetops.vehicles.application.event;

import java.util.Map;

/**
 * Mensaje recibido de la cola, ya leído del sobre.
 *
 * @param eventId identificador del evento; es texto porque algunos grupos no usan UUID
 * @param eventType nombre del evento
 * @param correlationId id de correlación del sobre, si trae uno
 * @param data contenido del evento
 */
public record IncomingEvent(String eventId, String eventType, String correlationId, Map<String, Object> data) {
}
