package co.edu.univalle.fleetops.vehicles.domain.rule;

import co.edu.univalle.fleetops.vehicles.domain.exception.FieldError;
import co.edu.univalle.fleetops.vehicles.domain.exception.ValidationException;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleDetails;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validaciones de los campos del vehículo (sección 2 del documento base). Acumulan todos los
 * errores y lanzan un único {@link ValidationException}.
 */
public final class VehicleValidationRules {

    private static final String REQUIRED = "REQUIRED";
    private static final String INVALID_FORMAT = "INVALID_FORMAT";
    private static final String OUT_OF_RANGE = "OUT_OF_RANGE";
    private static final String TOO_LONG = "TOO_LONG";

    private static final Pattern PLATE_PATTERN = Pattern.compile("^[A-Z]{3}[0-9]{3}$");
    private static final Pattern PLATE_SEPARATORS = Pattern.compile("[\\s-]");

    private static final int MIN_YEAR = 1980;
    private static final int BRAND_MIN = 2;
    private static final int BRAND_MAX = 50;
    private static final int MODEL_MIN = 1;
    private static final int MODEL_MAX = 50;
    private static final int COLOR_MAX = 30;
    private static final int NOTES_MAX = 500;
    private static final BigDecimal ODOMETER_MAX = BigDecimal.valueOf(2_000_000);

    private VehicleValidationRules() {
    }

    /**
     * Normaliza la placa a mayúsculas sin espacios ni guiones.
     *
     * @param rawPlate placa tal como llegó, puede ser nula
     * @return la placa normalizada, o nulo si llegó nula
     */
    public static String normalizePlate(String rawPlate) {
        if (rawPlate == null) {
            return null;
        }
        return PLATE_SEPARATORS.matcher(rawPlate).replaceAll("").toUpperCase(Locale.ROOT);
    }

    /**
     * Valida los datos de un alta.
     *
     * @param plate placa ya normalizada
     * @param type tipo de vehículo
     * @param details datos operativos
     * @param odometerKm odómetro inicial
     * @param currentYear año actual, para el límite superior del año de fabricación
     * @throws ValidationException si algún campo es inválido
     */
    public static void validateNewVehicle(String plate, VehicleType type, VehicleDetails details,
                                          BigDecimal odometerKm, int currentYear) {
        List<FieldError> errors = new ArrayList<>();
        checkPlate(errors, plate);
        if (type == null) {
            errors.add(new FieldError("vehicle_type", REQUIRED));
        }
        checkDetails(errors, details, currentYear);
        checkOdometer(errors, odometerKm);
        throwIfAny(errors);
    }

    /**
     * Valida los datos operativos de una edición.
     *
     * @param details datos operativos
     * @param currentYear año actual, para el límite superior del año de fabricación
     * @throws ValidationException si algún campo es inválido
     */
    public static void validateDetails(VehicleDetails details, int currentYear) {
        List<FieldError> errors = new ArrayList<>();
        checkDetails(errors, details, currentYear);
        throwIfAny(errors);
    }

    private static void checkPlate(List<FieldError> errors, String plate) {
        if (plate == null || plate.isEmpty()) {
            errors.add(new FieldError("plate", REQUIRED));
        } else if (!PLATE_PATTERN.matcher(plate).matches()) {
            errors.add(new FieldError("plate", INVALID_FORMAT));
        }
    }

    private static void checkDetails(List<FieldError> errors, VehicleDetails details, int currentYear) {
        checkRequiredText(errors, "brand", details.brand(), BRAND_MIN, BRAND_MAX);
        checkRequiredText(errors, "model", details.model(), MODEL_MIN, MODEL_MAX);
        checkYear(errors, details.year(), currentYear);
        checkOptionalText(errors, "color", details.color(), COLOR_MAX);
        checkOptionalText(errors, "notes", details.notes(), NOTES_MAX);
    }

    private static void checkRequiredText(List<FieldError> errors, String field, String value, int min, int max) {
        if (value == null || value.isBlank()) {
            errors.add(new FieldError(field, REQUIRED));
        } else if (value.length() < min) {
            errors.add(new FieldError(field, INVALID_FORMAT));
        } else if (value.length() > max) {
            errors.add(new FieldError(field, TOO_LONG));
        }
    }

    private static void checkOptionalText(List<FieldError> errors, String field, String value, int max) {
        if (value != null && value.length() > max) {
            errors.add(new FieldError(field, TOO_LONG));
        }
    }

    private static void checkYear(List<FieldError> errors, Integer year, int currentYear) {
        if (year == null) {
            errors.add(new FieldError("year", REQUIRED));
        } else if (year < MIN_YEAR || year > currentYear + 1) {
            errors.add(new FieldError("year", OUT_OF_RANGE));
        }
    }

    private static void checkOdometer(List<FieldError> errors, BigDecimal odometerKm) {
        if (odometerKm == null) {
            errors.add(new FieldError("odometer_km", REQUIRED));
        } else if (odometerKm.signum() < 0 || odometerKm.compareTo(ODOMETER_MAX) > 0) {
            errors.add(new FieldError("odometer_km", OUT_OF_RANGE));
        }
    }

    private static void throwIfAny(List<FieldError> errors) {
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }
}
