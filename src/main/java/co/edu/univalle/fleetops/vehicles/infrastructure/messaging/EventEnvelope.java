package co.edu.univalle.fleetops.vehicles.infrastructure.messaging;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Sobre estándar de los eventos del curso (hoja Convenciones del catálogo). Con la estrategia
 * snake_case de Jackson se escribe como {@code event_id}, {@code event_type}, {@code occurred_at}…
 *
 * @param eventId UUID único del mensaje
 * @param eventType nombre del evento
 * @param occurredAt fecha y hora en GMT-5
 * @param source slug del microservicio que publica
 * @param correlationId id de la petición o del mensaje que originó el evento
 * @param data contenido del evento
 */
public record EventEnvelope(UUID eventId, String eventType, OffsetDateTime occurredAt, String source,
                            String correlationId, Map<String, Object> data) {

    /** Slug de Vehículos en la hoja Convenciones. */
    public static final String VEHICLE_SOURCE = "vehicle";
}
