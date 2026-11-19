package co.edu.univalle.fleetops.vehicles.infrastructure.web.error;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.univalle.fleetops.vehicles.application.exception.AssignmentServiceUnavailableException;
import co.edu.univalle.fleetops.vehicles.application.exception.ConcurrentVehicleUpdateException;
import co.edu.univalle.fleetops.vehicles.application.exception.DocumentServiceUnavailableException;
import co.edu.univalle.fleetops.vehicles.domain.exception.DomainException;
import co.edu.univalle.fleetops.vehicles.domain.exception.InvalidStatusTransitionException;
import co.edu.univalle.fleetops.vehicles.domain.exception.StatusNotAllowedException;
import co.edu.univalle.fleetops.vehicles.domain.exception.VehicleHasActiveAssignmentsException;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.CannotCreateTransactionException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(CLOCK);
    private final MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/vehicles/x/status");
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private ch.qos.logback.classic.Logger handlerLogger;

    @BeforeEach
    void captureLogs() {
        handlerLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logs.start();
        handlerLogger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        handlerLogger.detachAppender(logs);
        logs.stop();
    }

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

    static Stream<Exception> databaseFailures() {
        return Stream.of(new DataAccessResourceFailureException("secret connection detail"),
                new QueryTimeoutException("secret connection detail"),
                new RecoverableDataAccessException("secret connection detail"),
                new CannotCreateTransactionException("secret connection detail"));
    }

    @ParameterizedTest
    @MethodSource("databaseFailures")
    void handleDatabaseUnavailable_returns503WithoutInternalDetails(Exception exception) {
        ResponseEntity<ApiErrorResponse> response = handler.handleDatabaseUnavailable(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(response.getBody().message()).doesNotContain("secret");
        assertThat(response.getBody().path()).isEqualTo("/vehicles/x/status");
        assertThat(response.getBody().errors()).isNull();
    }

    @Test
    void handleConcurrentUpdate_returns503WithoutInternalDetails() {
        var exception = new ConcurrentVehicleUpdateException(new RuntimeException("secret row detail"));

        ResponseEntity<ApiErrorResponse> response = handler.handleConcurrentUpdate(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(response.getBody().message()).doesNotContain("secret");
        assertThat(response.getBody().path()).isEqualTo("/vehicles/x/status");
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().errors()).isNull();
    }

    @Test
    void handleConcurrentUpdate_logsOneWarnWithThePathAndNoStackTrace() {
        handler.handleConcurrentUpdate(new ConcurrentVehicleUpdateException(new RuntimeException("stale")), request);

        List<ILoggingEvent> events = logs.list;
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).isEqualTo("Concurrent update conflict on /vehicles/x/status");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void handleConcurrentUpdate_neutralizesLineBreaksInTheLoggedPath() {
        var forgedPath = new MockHttpServletRequest("PUT", "/vehicles/x\r\nFORGED ERROR line");

        handler.handleConcurrentUpdate(new ConcurrentVehicleUpdateException(new RuntimeException()), forgedPath);

        assertThat(logs.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
                .isEqualTo("Concurrent update conflict on /vehicles/x__FORGED ERROR line"));
    }

    @Test
    void handleDatabaseUnavailable_stillLogsAtErrorLevel() {
        handler.handleDatabaseUnavailable(new DataAccessResourceFailureException("down"), request);

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).isEqualTo("Database unavailable on /vehicles/x/status");
        });
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