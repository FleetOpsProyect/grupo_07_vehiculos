package co.edu.univalle.fleetops.vehicles.domain.exception;

/** No se da de baja un vehículo con asignaciones PENDING o IN_PROGRESS (409). */
public class VehicleHasActiveAssignmentsException extends DomainException {

    /**
     * Crea el error con la cantidad de asignaciones que reportó Asignaciones.
     *
     * @param activeAssignments asignaciones pendientes o en curso, mayor que cero
     */
    public VehicleHasActiveAssignmentsException(int activeAssignments) {
        super("VEHICLE_HAS_ACTIVE_ASSIGNMENTS",
                "The vehicle has " + activeAssignments + " pending or in-progress assignments");
    }
}
