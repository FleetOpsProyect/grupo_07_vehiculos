package co.edu.univalle.fleetops.vehicles.infrastructure.persistence.adapter;

import co.edu.univalle.fleetops.vehicles.application.event.PendingEvent;
import co.edu.univalle.fleetops.vehicles.application.port.out.EventOutbox;
import co.edu.univalle.fleetops.vehicles.application.port.out.OutboxQueue;
import co.edu.univalle.fleetops.vehicles.infrastructure.messaging.EventEnvelope;
import co.edu.univalle.fleetops.vehicles.infrastructure.messaging.EventEnvelopeFactory;
import co.edu.univalle.fleetops.vehicles.infrastructure.messaging.EventJsonMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Tabla {@code outbox_events}. Usa JDBC sobre el mismo {@code DataSource} que JPA: dentro de una
 * transacción del caso de uso comparte su conexión, así que el evento se confirma o se revierte
 * junto con el cambio que lo genera.
 */
@Component
public class JdbcOutboxAdapter implements EventOutbox, OutboxQueue {

    private final JdbcTemplate jdbc;
    private final EventEnvelopeFactory envelopes;
    private final EventJsonMapper json;

    /**
     * Crea el adaptador.
     *
     * @param jdbc acceso JDBC
     * @param envelopes fábrica del sobre del evento
     * @param json conversión del sobre a JSON
     */
    public JdbcOutboxAdapter(JdbcTemplate jdbc, EventEnvelopeFactory envelopes, EventJsonMapper json) {
        this.jdbc = jdbc;
        this.envelopes = envelopes;
        this.json = json;
    }

    @Override
    public void record(String eventType, UUID aggregateId, Map<String, Object> data) {
        EventEnvelope envelope = envelopes.create(eventType, data);
        jdbc.update("""
                INSERT INTO outbox_events (event_id, event_type, aggregate_id, payload, status, attempts, created_at)
                VALUES (?, ?, ?, CAST(? AS jsonb), 'PENDING', 0, ?)
                """, envelope.eventId(), eventType, aggregateId, json.toJson(envelope), envelope.occurredAt());
    }

    @Override
    public List<PendingEvent> lockPending(int limit) {
        return jdbc.query("""
                SELECT event_id, event_type, CAST(payload AS text) AS payload
                FROM outbox_events
                WHERE status = 'PENDING'
                ORDER BY created_at, event_id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new PendingEvent(rs.getObject("event_id", UUID.class),
                rs.getString("event_type"), rs.getString("payload")), limit);
    }

    @Override
    public void markPublished(UUID eventId, OffsetDateTime publishedAt) {
        jdbc.update("UPDATE outbox_events SET status = 'PUBLISHED', published_at = ?, last_error = NULL "
                + "WHERE event_id = ?", publishedAt, eventId);
    }

    @Override
    public void markFailed(UUID eventId, String error) {
        jdbc.update("UPDATE outbox_events SET attempts = attempts + 1, last_error = ? WHERE event_id = ?",
                error, eventId);
    }
}
