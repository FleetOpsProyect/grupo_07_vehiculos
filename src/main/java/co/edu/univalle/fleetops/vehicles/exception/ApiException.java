package co.edu.univalle.fleetops.vehicles.exception;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Excepción base de la API: cada subclase define su código HTTP.
 * El GlobalExceptionHandler la convierte en ApiErrorResponse.
 */
public abstract class ApiException extends RuntimeException {

    /** Código HTTP de la respuesta. */
    private final HttpStatus status;

    /** Código de error estable para los consumidores, ej. VEHICLE_NOT_FOUND. */
    private final String code;

    /**
     * Crea la excepción.
     * @param status código HTTP
     * @param code código de error
     * @param message mensaje descriptivo
     */
    protected ApiException(final HttpStatus status, final String code, final String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /**
     * Código HTTP de la respuesta.
     * @return código HTTP
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * Código de error estable.
     * @return código de error
     */
    public String getCode() {
        return code;
    }

    /**
     * Errores por campo; vacío salvo en errores de validación.
     * @return lista de errores por campo
     */
    public List<FieldErrorDetail> getErrors() {
        return List.of();
    }
}
