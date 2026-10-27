package co.edu.univalle.fleetops.vehicles.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Manejador global de excepciones: toda respuesta de error usa ApiErrorResponse. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Zona horaria de FleetOps (GMT-5). */
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    /** Logger para errores no controlados. */
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Maneja las excepciones propias de la API (400, 404, 409, 422 y 503).
     * @param ex excepción lanzada por el service o un cliente HTTP
     * @param request petición HTTP que originó el error
     * @return respuesta con formato estándar de error
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(
            final ApiException ex,
            final HttpServletRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getErrors(), request);
    }

    /**
     * Maneja la modificación concurrente del mismo vehículo — devuelve HTTP 409.
     * @param ex excepción de bloqueo optimista
     * @param request petición HTTP que originó el error
     * @return respuesta con formato estándar de error
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(
            final ObjectOptimisticLockingFailureException ex,
            final HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                "El vehículo fue modificado por otra operación; intente de nuevo", List.of(), request);
    }

    /**
     * Maneja violaciones de restricciones de la BD (placa única, CHECK) — devuelve HTTP 409.
     * @param ex excepción de JPA
     * @param request petición HTTP que originó el error
     * @return respuesta con formato estándar de error
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
            final DataIntegrityViolationException ex,
            final HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "La operación viola una restricción de la base de datos", List.of(), request);
    }

    /**
     * Maneja peticiones mal formadas: JSON inválido, UUID o enum incorrecto,
     * fecha mal escrita o parámetro faltante — devuelve HTTP 400.
     * @param ex excepción de Spring MVC
     * @param request petición HTTP que originó el error
     * @return respuesta con formato estándar de error
     */
    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
            final Exception ex,
            final HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "La petición no tiene el formato esperado", List.of(), request);
    }

    /**
     * Maneja rutas que no existen — devuelve HTTP 404.
     * @param ex excepción de Spring MVC
     * @param request petición HTTP que originó el error
     * @return respuesta con formato estándar de error
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(
            final NoResourceFoundException ex,
            final HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND", "Ruta no encontrada", List.of(), request);
    }

    /**
     * Maneja cualquier excepción no controlada — devuelve HTTP 500 sin detalles internos.
     * @param ex excepción no controlada
     * @param request petición HTTP que originó el error
     * @return respuesta con formato estándar de error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneral(
            final Exception ex,
            final HttpServletRequest request) {
        LOG.error("Error no controlado en {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Error interno del servidor", List.of(), request);
    }

    private ResponseEntity<ApiErrorResponse> build(
            final HttpStatus status,
            final String code,
            final String message,
            final List<FieldErrorDetail> errors,
            final HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(status.value(), code, message,
                request.getRequestURI(), OffsetDateTime.now(BOGOTA), errors);
        return ResponseEntity.status(status).body(body);
    }
}
