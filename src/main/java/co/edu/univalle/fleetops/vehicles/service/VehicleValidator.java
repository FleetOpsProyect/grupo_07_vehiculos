package co.edu.univalle.fleetops.vehicles.service;

import co.edu.univalle.fleetops.vehicles.dto.ChangeStatusRequest;
import co.edu.univalle.fleetops.vehicles.dto.CreateVehicleRequest;
import co.edu.univalle.fleetops.vehicles.dto.UpdateVehicleRequest;
import co.edu.univalle.fleetops.vehicles.exception.BusinessRuleException;
import co.edu.univalle.fleetops.vehicles.exception.FieldErrorDetail;
import co.edu.univalle.fleetops.vehicles.exception.ValidationException;
import co.edu.univalle.fleetops.vehicles.model.VehicleStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Validaciones de entrada del MS Vehículos. Reúne todos los errores de una petición
 * antes de responder, como pide la convención de FleetOps.
 */
@Component
public class VehicleValidator {

    /** Formato de placa colombiana de vehículo particular: tres letras y tres números. */
    private static final Pattern PLATE_PATTERN = Pattern.compile("^[A-Z]{3}\\d{3}$");

    /** Año mínimo aceptado. */
    private static final int MIN_YEAR = 1980;

    /** Kilometraje máximo aceptado. */
    private static final BigDecimal MAX_ODOMETER = new BigDecimal("2000000");

    /** Tamaño máximo de página. */
    private static final int MAX_PAGE_SIZE = 100;

    private static final String REQUIRED = "REQUIRED";
    private static final String OUT_OF_RANGE = "OUT_OF_RANGE";
    private static final String TOO_LONG = "TOO_LONG";
    private static final String NOT_EDITABLE = "NOT_EDITABLE";

    /** Reloj para conocer el año actual. */
    private final Clock clock;

    /**
     * Constructor con inyección de dependencias.
     * @param clock reloj de la aplicación
     */
    public VehicleValidator(final Clock clock) {
        this.clock = clock;
    }

    /**
     * Valida el registro de un vehículo y normaliza la placa.
     * @param request datos del vehículo
     * @return placa normalizada
     * @throws ValidationException si algún campo es inválido
     */
    public String validateCreate(final CreateVehicleRequest request) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        String plate = normalizePlate(request.plate());
        if (plate == null) {
            errors.add(new FieldErrorDetail("plate", REQUIRED));
        } else if (!PLATE_PATTERN.matcher(plate).matches()) {
            errors.add(new FieldErrorDetail("plate", "INVALID_FORMAT"));
        }
        if (request.vehicleType() == null) {
            errors.add(new FieldErrorDetail("vehicle_type", REQUIRED));
        }
        validateEditableFields(request.brand(), request.model(), request.year(),
                request.color(), request.notes(), errors);
        if (request.odometerKm() == null) {
            errors.add(new FieldErrorDetail("odometer_km", REQUIRED));
        } else if (request.odometerKm().signum() < 0 || request.odometerKm().compareTo(MAX_ODOMETER) > 0) {
            errors.add(new FieldErrorDetail("odometer_km", OUT_OF_RANGE));
        }
        throwIfAny(errors);
        return plate;
    }

    /**
     * Valida la modificación de datos: los campos editables y que no lleguen los no editables.
     * @param request datos nuevos
     * @throws ValidationException si algún campo es inválido o no editable
     */
    public void validateUpdate(final UpdateVehicleRequest request) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        addIfPresent(request.plate(), "plate", errors);
        addIfPresent(request.vehicleType(), "vehicle_type", errors);
        addIfPresent(request.odometerKm(), "odometer_km", errors);
        addIfPresent(request.status(), "status", errors);
        validateEditableFields(request.brand(), request.model(), request.year(),
                request.color(), request.notes(), errors);
        throwIfAny(errors);
    }

    /**
     * Valida la petición del scheduler de Mantenimiento.
     * @param request petición de cambio de estado
     * @throws BusinessRuleException si el estado pedido no es IN_MAINTENANCE (422)
     * @throws ValidationException si falta la orden de mantenimiento o el motivo es muy largo
     */
    public void validateStatusChange(final ChangeStatusRequest request) {
        if (request.status() != VehicleStatus.IN_MAINTENANCE) {
            throw new BusinessRuleException("STATUS_NOT_ALLOWED",
                    "Este endpoint solo permite pasar a IN_MAINTENANCE; los demás cambios llegan por eventos");
        }
        List<FieldErrorDetail> errors = new ArrayList<>();
        if (request.maintenanceId() == null) {
            errors.add(new FieldErrorDetail("maintenance_id", REQUIRED));
        }
        if (request.reason() != null && request.reason().length() > 100) {
            errors.add(new FieldErrorDetail("reason", TOO_LONG));
        }
        throwIfAny(errors);
    }

    /**
     * Valida los parámetros de paginación.
     * @param page número de página, desde 0
     * @param size tamaño de página, de 1 a 100
     * @throws ValidationException si están fuera de rango
     */
    public void validatePage(final int page, final int size) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        if (page < 0) {
            errors.add(new FieldErrorDetail("page", OUT_OF_RANGE));
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            errors.add(new FieldErrorDetail("size", OUT_OF_RANGE));
        }
        throwIfAny(errors);
    }

    /**
     * Normaliza una placa: mayúsculas, sin espacios ni guiones ("abc-123" → "ABC123").
     * @param rawPlate placa como la escribió el usuario
     * @return placa normalizada, o null si viene vacía
     */
    public static String normalizePlate(final String rawPlate) {
        if (rawPlate == null || rawPlate.isBlank()) {
            return null;
        }
        return rawPlate.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private void validateEditableFields(
            final String brand,
            final String model,
            final Integer year,
            final String color,
            final String notes,
            final List<FieldErrorDetail> errors) {
        requiredText(brand, "brand", 50, errors);
        requiredText(model, "model", 50, errors);
        if (year == null) {
            errors.add(new FieldErrorDetail("year", REQUIRED));
        } else if (year < MIN_YEAR || year > Year.now(clock).getValue() + 1) {
            errors.add(new FieldErrorDetail("year", OUT_OF_RANGE));
        }
        if (color != null && color.length() > 30) {
            errors.add(new FieldErrorDetail("color", TOO_LONG));
        }
        if (notes != null && notes.length() > 500) {
            errors.add(new FieldErrorDetail("notes", TOO_LONG));
        }
    }

    private static void requiredText(
            final String value,
            final String field,
            final int maxLength,
            final List<FieldErrorDetail> errors) {
        if (value == null || value.isBlank()) {
            errors.add(new FieldErrorDetail(field, REQUIRED));
        } else if (value.trim().length() > maxLength) {
            errors.add(new FieldErrorDetail(field, TOO_LONG));
        }
    }

    private static void addIfPresent(final Object value, final String field, final List<FieldErrorDetail> errors) {
        if (value != null) {
            errors.add(new FieldErrorDetail(field, NOT_EDITABLE));
        }
    }

    private static void throwIfAny(final List<FieldErrorDetail> errors) {
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }
}
