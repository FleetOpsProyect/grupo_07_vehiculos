package co.edu.univalle.fleetops.vehicles.application.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ServiceUnavailableExceptionsTest {

    @Test
    void documentServiceUnavailable_keepsCause() {
        var cause = new RuntimeException("timeout");

        var ex = new DocumentServiceUnavailableException(cause);

        assertThat(ex.code()).isEqualTo("DOCUMENT_SERVICE_UNAVAILABLE");
        assertThat(ex).hasCause(cause);
    }

    @Test
    void assignmentServiceUnavailable_keepsCause() {
        var cause = new RuntimeException("timeout");

        var ex = new AssignmentServiceUnavailableException(cause);

        assertThat(ex.code()).isEqualTo("ASSIGNMENT_SERVICE_UNAVAILABLE");
        assertThat(ex).hasCause(cause);
    }
}
