package co.edu.univalle.fleetops.vehicles.domain.exception;

/** El endpoint de estado solo acepta IN_MAINTENANCE (422 {@code STATUS_NOT_ALLOWED}). */
public class StatusNotAllowedException extends DomainException {

    /** Crea el error con el texto del contrato. */
    public StatusNotAllowedException() {
        super("STATUS_NOT_ALLOWED",
                "This endpoint only allows IN_MAINTENANCE; other changes arrive through events");
    }
}
