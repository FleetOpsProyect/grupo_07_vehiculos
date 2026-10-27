package co.edu.univalle.fleetops.vehicles.exception;

import java.util.List;
import org.springframework.http.HttpStatus;

/** Datos inválidos: HTTP 400 con todos los campos que fallaron, no solo el primero. */
public class ValidationException extends ApiException {

    /** Errores por campo. */
    private final transient List<FieldErrorDetail> errors;

    /**
     * Crea la excepción.
     * @param errors errores por campo
     */
    public ValidationException(final List<FieldErrorDetail> errors) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La petición tiene campos inválidos");
        this.errors = List.copyOf(errors);
    }

    @Override
    public List<FieldErrorDetail> getErrors() {
        return errors;
    }
}
