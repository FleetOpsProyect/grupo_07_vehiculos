package co.edu.univalle.fleetops.vehicles.application.port.out;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Rutas cuya distancia ya se sumó al odómetro: cada ruta se suma una sola vez. */
public interface AppliedRouteRepository {

    /**
     * Registra la ruta como aplicada, dentro de la transacción en curso.
     *
     * @param routeId ruta terminada
     * @param vehicleId vehículo de la ruta
     * @param distanceKm distancia sumada
     * @param appliedAt momento en que se sumó
     * @return {@code true} si es la primera vez, {@code false} si ya estaba aplicada
     */
    boolean markApplied(UUID routeId, UUID vehicleId, BigDecimal distanceKm, OffsetDateTime appliedAt);

    /**
     * Indica si la ruta ya terminó y se sumó.
     *
     * @param routeId ruta
     * @return {@code true} si ya está aplicada
     */
    boolean isApplied(UUID routeId);
}
