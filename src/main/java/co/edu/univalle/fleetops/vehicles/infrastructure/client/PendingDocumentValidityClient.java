package co.edu.univalle.fleetops.vehicles.infrastructure.client;

import co.edu.univalle.fleetops.vehicles.application.exception.DocumentServiceUnavailableException;
import co.edu.univalle.fleetops.vehicles.application.port.out.DocumentValidityPort;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * PENDIENTE (equipo): reemplazar por el cliente HTTP de Documentación (G1),
 * {@code GET /documents/validity-check}, con timeout de 2 s. Mientras tanto responde como si
 * Documentación estuviera caída, para no devolver nunca una lista sin validar.
 */
@Component
public class PendingDocumentValidityClient implements DocumentValidityPort {

    @Override
    public Set<UUID> findVehiclesWithValidDocuments(List<UUID> vehicleIds, LocalDate validUntil) {
        throw new DocumentServiceUnavailableException(
                new UnsupportedOperationException("G1 client not implemented yet"));
    }
}
