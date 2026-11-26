package co.edu.univalle.fleetops.vehicles.infrastructure.persistence.adapter;

import co.edu.univalle.fleetops.vehicles.application.port.out.AppliedRouteRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Tabla {@code applied_route}. {@code ON CONFLICT DO NOTHING} garantiza que una ruta se sume una sola vez,
 * aunque dos mensajes del mismo fin de ruta lleguen con distinto {@code event_id}.
 */
@Component
public class JdbcAppliedRouteAdapter implements AppliedRouteRepository {

    private final JdbcTemplate jdbc;

    /**
     * Crea el adaptador.
     *
     * @param jdbc acceso JDBC
     */
    public JdbcAppliedRouteAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean markApplied(UUID routeId, UUID vehicleId, BigDecimal distanceKm, OffsetDateTime appliedAt) {
        return jdbc.update("""
                INSERT INTO applied_route (route_id, vehicle_id, distance_km, applied_at) VALUES (?, ?, ?, ?)
                ON CONFLICT (route_id) DO NOTHING
                """, routeId, vehicleId, distanceKm, appliedAt) == 1;
    }

    @Override
    public boolean isApplied(UUID routeId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM applied_route WHERE route_id = ?)",
                Boolean.class, routeId);
        return Boolean.TRUE.equals(exists);
    }
}
