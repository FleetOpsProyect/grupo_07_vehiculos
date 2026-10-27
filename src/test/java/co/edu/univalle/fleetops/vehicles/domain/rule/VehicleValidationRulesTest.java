package co.edu.univalle.fleetops.vehicles.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.univalle.fleetops.vehicles.domain.exception.FieldError;
import co.edu.univalle.fleetops.vehicles.domain.exception.ValidationException;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleDetails;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class VehicleValidationRulesTest {

    private static final int YEAR = 2026;

    private static VehicleDetails validDetails() {
        return new VehicleDetails("Toyota", "Hilux", 2023, "White", "notes");
    }

    private static List<FieldError> errorsOfNew(String plate, VehicleType type, VehicleDetails details,
                                                BigDecimal odometer) {
        var thrown = catchThrowableOfType(ValidationException.class,
                () -> VehicleValidationRules.validateNewVehicle(plate, type, details, odometer, YEAR));
        return thrown.errors();
    }

    private static <T extends Throwable> T catchThrowableOfType(Class<T> type,
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return org.assertj.core.api.Assertions.catchThrowableOfType(type, callable);
    }

    @Test
    void normalizePlate_uppercasesAndRemovesSpacesAndHyphens() {
        assertThat(VehicleValidationRules.normalizePlate(" abc-1 23 ")).isEqualTo("ABC123");
    }

    @Test
    void normalizePlate_null_returnsNull() {
        assertThat(VehicleValidationRules.normalizePlate(null)).isNull();
    }

    @Test
    void validateNewVehicle_validData_doesNotThrow() {
        assertThatCode(() -> VehicleValidationRules.validateNewVehicle("ABC123", VehicleType.CAR,
                validDetails(), new BigDecimal("15200"), YEAR)).doesNotThrowAnyException();
    }

    @Test
    void validateNewVehicle_boundaryValues_doNotThrow() {
        var details = new VehicleDetails("To", "H", 1980, "c".repeat(30), "n".repeat(500));

        assertThatCode(() -> VehicleValidationRules.validateNewVehicle("ABC123", VehicleType.CAR, details,
                new BigDecimal("2000000"), YEAR)).doesNotThrowAnyException();
        assertThatCode(() -> VehicleValidationRules.validateNewVehicle("ABC123", VehicleType.CAR,
                new VehicleDetails("Toyota", "Hilux", YEAR + 1, null, null), BigDecimal.ZERO, YEAR))
                .doesNotThrowAnyException();
    }

    @Test
    void validateNewVehicle_missingEverything_reportsAllRequired() {
        var details = new VehicleDetails(null, " ", null, null, null);

        var errors = errorsOfNew("", null, details, null);

        assertThat(errors).containsExactlyInAnyOrder(new FieldError("plate", "REQUIRED"),
                new FieldError("vehicle_type", "REQUIRED"), new FieldError("brand", "REQUIRED"),
                new FieldError("model", "REQUIRED"), new FieldError("year", "REQUIRED"),
                new FieldError("odometer_km", "REQUIRED"));
    }

    @Test
    void validateNewVehicle_nullPlate_isRequired() {
        assertThat(errorsOfNew(null, VehicleType.CAR, validDetails(), BigDecimal.ONE))
                .containsExactly(new FieldError("plate", "REQUIRED"));
    }

    @Test
    void validateNewVehicle_badPlateFormat_isInvalidFormat() {
        assertThat(errorsOfNew("AB1234", VehicleType.CAR, validDetails(), BigDecimal.ONE))
                .containsExactly(new FieldError("plate", "INVALID_FORMAT"));
    }

    @Test
    void validateNewVehicle_brandTooShortOrTooLong() {
        var shortBrand = new VehicleDetails("T", "Hilux", 2023, null, null);
        var longBrand = new VehicleDetails("T".repeat(51), "Hilux", 2023, null, null);

        assertThat(errorsOfNew("ABC123", VehicleType.CAR, shortBrand, BigDecimal.ONE))
                .containsExactly(new FieldError("brand", "INVALID_FORMAT"));
        assertThat(errorsOfNew("ABC123", VehicleType.CAR, longBrand, BigDecimal.ONE))
                .containsExactly(new FieldError("brand", "TOO_LONG"));
    }

    @Test
    void validateNewVehicle_modelTooLong() {
        var details = new VehicleDetails("Toyota", "H".repeat(51), 2023, null, null);

        assertThat(errorsOfNew("ABC123", VehicleType.CAR, details, BigDecimal.ONE))
                .containsExactly(new FieldError("model", "TOO_LONG"));
    }

    @Test
    void validateNewVehicle_yearOutOfRange() {
        var tooOld = new VehicleDetails("Toyota", "Hilux", 1979, null, null);
        var tooNew = new VehicleDetails("Toyota", "Hilux", YEAR + 2, null, null);

        assertThat(errorsOfNew("ABC123", VehicleType.CAR, tooOld, BigDecimal.ONE))
                .containsExactly(new FieldError("year", "OUT_OF_RANGE"));
        assertThat(errorsOfNew("ABC123", VehicleType.CAR, tooNew, BigDecimal.ONE))
                .containsExactly(new FieldError("year", "OUT_OF_RANGE"));
    }

    @Test
    void validateNewVehicle_colorAndNotesTooLong() {
        var details = new VehicleDetails("Toyota", "Hilux", 2023, "c".repeat(31), "n".repeat(501));

        assertThat(errorsOfNew("ABC123", VehicleType.CAR, details, BigDecimal.ONE))
                .containsExactlyInAnyOrder(new FieldError("color", "TOO_LONG"),
                        new FieldError("notes", "TOO_LONG"));
    }

    @Test
    void validateNewVehicle_odometerOutOfRange() {
        assertThat(errorsOfNew("ABC123", VehicleType.CAR, validDetails(), new BigDecimal("-0.1")))
                .containsExactly(new FieldError("odometer_km", "OUT_OF_RANGE"));
        assertThat(errorsOfNew("ABC123", VehicleType.CAR, validDetails(), new BigDecimal("2000000.1")))
                .containsExactly(new FieldError("odometer_km", "OUT_OF_RANGE"));
    }

    @Test
    void validateDetails_valid_doesNotThrow() {
        assertThatCode(() -> VehicleValidationRules.validateDetails(validDetails(), YEAR))
                .doesNotThrowAnyException();
    }

    @Test
    void validateDetails_invalid_throwsWithFieldErrors() {
        var details = new VehicleDetails("", "Hilux", 2023, null, null);

        assertThatThrownBy(() -> VehicleValidationRules.validateDetails(details, YEAR))
                .isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.errors())
                        .containsExactly(new FieldError("brand", "REQUIRED")));
    }
}
