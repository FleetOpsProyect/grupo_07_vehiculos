package co.edu.univalle.fleetops.vehicles.infrastructure.web.error;

import co.edu.univalle.fleetops.vehicles.domain.exception.DomainException;
import co.edu.univalle.fleetops.vehicles.domain.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Traduce las excepciones al formato de error del contrato. Los códigos HTTP salen de la tabla de abajo. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Map<String, HttpStatus> STATUS_BY_CODE = Map.of(
            "VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
            "VEHICLE_NOT_FOUND", HttpStatus.NOT_FOUND,
            "PLATE_ALREADY_EXISTS", HttpStatus.CONFLICT,
            "INVALID_STATUS_TRANSITION", HttpStatus.CONFLICT,
            "VEHICLE_HAS_ACTIVE_ASSIGNMENTS", HttpStatus.CONFLICT,
            "STATUS_NOT_ALLOWED", HttpStatus.UNPROCESSABLE_ENTITY,
            "DOCUMENT_SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
            "ASSIGNMENT_SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);

    private final Clock clock;

    /**
     * Crea el manejador.
     *
     * @param clock reloj en GMT-5 para el campo {@code timestamp}
     */
    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    /**
     * Errores de negocio y de validación del dominio y la aplicación.
     *
     * @param exception error con su código del contrato
     * @param request petición en curso
     * @return la respuesta con el código HTTP que corresponde al código del error
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomain(DomainException exception, HttpServletRequest request) {
        HttpStatus status = STATUS_BY_CODE.getOrDefault(exception.code(), HttpStatus.INTERNAL_SERVER_ERROR);
        List<FieldErrorResponse> errors = null;
        if (exception instanceof ValidationException validation) {
            errors = validation.errors().stream()
                    .map(error -> new FieldErrorResponse(error.field(), error.code())).toList();
        }
        return build(status, exception.code(), exception.getMessage(), request, errors);
    }

    /**
     * Id de ruta que no es un UUID: se trata como un vehículo inexistente.
     *
     * @param exception error de la capa REST
     * @param request petición en curso
     * @return 404 {@code VEHICLE_NOT_FOUND}
     */
    @ExceptionHandler(InvalidVehicleIdException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidVehicleId(InvalidVehicleIdException exception,
            HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "VEHICLE_NOT_FOUND", exception.getMessage(), request, null);
    }

    /**
     * Cuerpo ilegible: JSON mal formado, tipo incorrecto o valor de enum desconocido.
     *
     * @param exception error de Jackson envuelto por Spring
     * @param request petición en curso
     * @return 400 {@code VALIDATION_ERROR}, con el campo si se pudo identificar
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        List<FieldErrorResponse> errors = List.of();
        if (exception.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            errors = List.of(new FieldErrorResponse(toSnakeCase(mapping.getPath().get(0).getFieldName()),
                    "INVALID_FORMAT"));
        }
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request body is not valid", request, errors);
    }

    /**
     * Parámetro de consulta o de ruta con un tipo incorrecto.
     *
     * @param exception error de conversión
     * @param request petición en curso
     * @return 400 {@code VALIDATION_ERROR} con el parámetro como campo
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request has invalid fields", request,
                List.of(new FieldErrorResponse(toSnakeCase(exception.getName()), "INVALID_FORMAT")));
    }

    /**
     * Cualquier otra falla: se registra y se responde sin detalles internos.
     *
     * @param exception error inesperado
     * @param request petición en curso
     * @return 500 {@code INTERNAL_ERROR}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOG.error("Unexpected error on {}", request.getRequestURI(), exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error", request, null);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message,
            HttpServletRequest request, List<FieldErrorResponse> errors) {
        ApiErrorResponse body = new ApiErrorResponse(code, message, OffsetDateTime.now(clock),
                request.getRequestURI(), errors);
        return ResponseEntity.status(status).body(body);
    }

    private static String toSnakeCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
