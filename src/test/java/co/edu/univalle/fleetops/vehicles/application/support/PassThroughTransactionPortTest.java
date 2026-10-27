package co.edu.univalle.fleetops.vehicles.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PassThroughTransactionPortTest {

    @Test
    void execute_returnsActionResult() {
        var port = new PassThroughTransactionPort();

        String result = port.execute(() -> "ok");

        assertThat(result).isEqualTo("ok");
    }
}
