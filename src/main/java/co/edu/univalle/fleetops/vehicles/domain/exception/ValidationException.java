package co.edu.univalle.fleetops.vehicles.domain.exception;

import java.util.List;

/** Uno o más campos inválidos (400 {@code VALIDATION_ERROR}). */
public class ValidationException extends DomainException {

    private final List<FieldError> errors;

    /**
     * Crea el error con todos los campos inválidos.
     *
     * @param errors campos inválidos con su código
     */
    public ValidationException(List<FieldError> errors) {
        super("VALIDATION_ERROR", "The request has invalid fields");
        this.errors = List.copyOf(errors);
    }

    /**
     * Devuelve los campos inválidos.
     *
     * @return lista inmutable de errores por campo
     */
    public List<FieldError> errors() {
        return errors;
    }
}
