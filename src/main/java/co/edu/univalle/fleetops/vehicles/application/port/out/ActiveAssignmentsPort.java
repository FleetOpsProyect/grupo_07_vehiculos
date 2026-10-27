package co.edu.univalle.fleetops.vehicles.application.port.out;

import java.util.UUID;

/** Consulta a Asignaciones (G2) de las asignaciones PENDING e IN_PROGRESS de un vehículo. */
public interface ActiveAssignmentsPort {

    /**
     * Cuenta las asignaciones pendientes o en curso.
     *
     * @param vehicleId vehículo consultado
     * @return el campo {@code total} de la respuesta de Asignaciones
     * @throws co.edu.univalle.fleetops.vehicles.application.exception.AssignmentServiceUnavailableException
     *     si Asignaciones no responde en 2 s
     */
    int countActiveAssignments(UUID vehicleId);
}
