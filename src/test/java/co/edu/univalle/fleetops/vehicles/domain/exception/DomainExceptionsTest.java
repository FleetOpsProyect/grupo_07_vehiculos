package co.edu.univalle.fleetops.vehicles.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainExceptionsTest {

    @Test
    void vehicleNotFound_hasContractCodeAndMessage() {
        UUID id = UUID.randomUUID();

        var ex = new VehicleNotFoundException(id);

        assertThat(ex.code()).isEqualTo("VEHICLE_NOT_FOUND");
        assertThat(ex).hasMessage("No vehicle exists with id " + id);
    }

    @Test
    void plateAlreadyExists_hasContractCodeAndMessage() {
        var ex = new PlateAlreadyExistsException("ABC123");

        assertThat(ex.code()).isEqualTo("PLATE_ALREADY_EXISTS");
        assertThat(ex).hasMessage("A vehicle with plate ABC123 already exists");
    }

    @Test
    void invalidStatusTransition_hasContractCodeAndMessage() {
        var ex = new InvalidStatusTransitionException(VehicleStatus.ON_ROUTE);

        assertThat(ex.code()).isEqualTo("INVALID_STATUS_TRANSITION");
        assertThat(ex).hasMessage("The vehicle is ON_ROUTE; it can only go to maintenance from ACTIVE");
    }

    @Test
    void statusNotAllowed_hasContractCodeAndMessage() {
        var ex = new StatusNotAllowedException();

        assertThat(ex.code()).isEqualTo("STATUS_NOT_ALLOWED");
        assertThat(ex).hasMessage("This endpoint only allows IN_MAINTENANCE; other changes arrive through events");
    }

    @Test
    void vehicleHasActiveAssignments_hasContractCodeAndMessage() {
        var ex = new VehicleHasActiveAssignmentsException(2);

        assertThat(ex.code()).isEqualTo("VEHICLE_HAS_ACTIVE_ASSIGNMENTS");
        assertThat(ex).hasMessage("The vehicle has 2 pending or in-progress assignments");
    }

    @Test
    void validationException_copiesErrors() {
        List<FieldError> source = new ArrayList<>(List.of(new FieldError("plate", "REQUIRED")));

        var ex = new ValidationException(source);
        source.clear();

        assertThat(ex.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(ex.errors()).containsExactly(new FieldError("plate", "REQUIRED"));
    }
}
