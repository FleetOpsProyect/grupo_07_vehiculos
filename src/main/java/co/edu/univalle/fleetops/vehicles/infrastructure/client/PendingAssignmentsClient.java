package co.edu.univalle.fleetops.vehicles.infrastructure.client;

import co.edu.univalle.fleetops.vehicles.application.exception.AssignmentServiceUnavailableException;
import co.edu.univalle.fleetops.vehicles.application.port.out.ActiveAssignmentsPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * PENDIENTE (equipo): reemplazar por el cliente HTTP de Asignaciones (G2),
 * {@code GET /assignment/active?vehicle_id={id}}, con timeout de 2 s. Mientras tanto responde
 * como si Asignaciones estuviera caída, para no borrar sin confirmar.
 */
@Component
public class PendingAssignmentsClient implements ActiveAssignmentsPort {

    @Override
    public int countActiveAssignments(UUID vehicleId) {
        throw new AssignmentServiceUnavailableException(
                new UnsupportedOperationException("G2 client not implemented yet"));
    }
}
