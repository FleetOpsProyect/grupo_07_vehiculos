package co.edu.univalle.fleetops.vehicles.infrastructure.web.error;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import co.edu.univalle.fleetops.vehicles.application.exception.AssignmentServiceUnavailableException;
import co.edu.univalle.fleetops.vehicles.application.exception.DocumentServiceUnavailableException;
import co.edu.univalle.fleetops.vehicles.domain.exception.DomainException;
import co.edu.univalle.fleetops.vehicles.domain.exception.InvalidStatusTransitionException;
import co.edu.univalle.fleetops.vehicles.domain.exception.StatusNotAllowedException;
import co.edu.univalle.fleetops.vehicles.domain.exception.VehicleHasActiveAssignmentsException;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(CLOCK);
    private final MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/vehicles/x/status");

    static Stream<Arguments> domainErrors() {
        var cause = new RuntimeException("timeout");
        return Stream.of(
                Arguments.of(new InvalidStatusTransitionException(VehicleStatus.ON_ROUTE), 409),
                Arguments.of(new VehicleHasActiveAssignmentsException(1), 409),
                Arguments.of(new StatusNotAllowedException(), 422),
                Arguments.of(new DocumentServiceUnavailableException(cause), 503),
                Arguments.of(new AssignmentServiceUnavailableException(cause), 503),
                Arguments.of(new DomainException("UNMAPPED_CODE", "x") {}, 500));
    }

    @ParameterizedTest
    @MethodSource("domainErrors")
    void handleDomain_mapsCodeToHttpStatus(DomainException exception, int expectedStatus) {
        ResponseEntity<ApiErrorResponse> response = handler.handleDomain(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        assertThat(response.getBody().code()).isEqualTo(exception.code());
        assertThat(response.getBody().path()).isEqualTo("/vehicles/x/status");
        assertThat(response.getBody().errors()).isNull();
    }

    @Test
    void handleUnexpected_returns500WithoutInternalDetails() {
        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(new IllegalStateException("secret"),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("secret");
    }

    @Test
    void handleInvalidVehicleId_returns404VehicleNotFound() {
        ResponseEntity<ApiErrorResponse> response = handler.handleInvalidVehicleId(
                new InvalidVehicleIdException("abc"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo("VEHICLE_NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("No vehicle exists with id abc");
    }
}
