package co.edu.univalle.fleetops.vehicles.infrastructure.persistence.adapter;

import co.edu.univalle.fleetops.vehicles.application.port.out.ProcessedEventRepository;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Tabla {@code processed_events}. {@code ON CONFLICT DO NOTHING} deja la decisión a la base de datos:
 * si dos mensajes con el mismo {@code event_id} llegan a la vez, el segundo espera al primero y
 * después ve que ya existe.
 */
@Component
public class JdbcProcessedEventAdapter implements ProcessedEventRepository {

    private final JdbcTemplate jdbc;

    /**
     * Crea el adaptador.
     *
     * @param jdbc acceso JDBC
     */
    public JdbcProcessedEventAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean markProcessed(String eventId, String eventType, OffsetDateTime processedAt) {
        return jdbc.update("""
                INSERT INTO processed_events (event_id, event_type, processed_at) VALUES (?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, eventType, processedAt) == 1;
    }
}
