package co.edu.univalle.fleetops.vehicles.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.univalle.fleetops.vehicles.application.exception.AssignmentServiceUnavailableException;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.filter.CorrelationIdFilter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

/** Cliente de Asignaciones contra un servidor HTTP real de la JDK, con tiempo de espera corto. */
class AssignmentsHttpClientTest {

    private static final long TIMEOUT_MS = 300;
    private static final UUID VEHICLE_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

    private HttpServer server;
    private AssignmentsHttpClient client;
    private final AtomicReference<String> receivedQuery = new AtomicReference<>();
    private final AtomicReference<String> receivedCorrelation = new AtomicReference<>();
    private volatile int status;
    private volatile String body;
    private volatile long delayMs;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/assignment/active", this::answer);
        server.start();
        client = new AssignmentsHttpClient(RestClient.builder(), baseUrl(), TIMEOUT_MS);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void answer(HttpExchange exchange) throws IOException {
        receivedQuery.set(exchange.getRequestURI().getQuery());
        receivedCorrelation.set(exchange.getRequestHeaders().getFirst(CorrelationIdFilter.HEADER));
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void respond(int newStatus, String newBody) {
        this.status = newStatus;
        this.body = newBody;
    }

    @Test
    void count_okResponse_returnsTheTotalAndSendsTheVehicleId() {
        respond(200, """
                {"content": [{"assignment_id": "4b3a2c1d-0e9f-4a8b-9c7d-6e5f4a3b2c1d", "status": "PENDING"}],
                 "page": 0, "size": 20, "total": 1}""");

        int total = client.countActiveAssignments(VEHICLE_ID);

        assertThat(total).isEqualTo(1);
        assertThat(receivedQuery.get()).isEqualTo("vehicle_id=" + VEHICLE_ID);
    }

    @Test
    void count_noAssignments_returnsZero() {
        respond(200, "{\"content\": [], \"page\": 0, \"size\": 20, \"total\": 0}");

        assertThat(client.countActiveAssignments(VEHICLE_ID)).isZero();
    }

    @Test
    void count_withCorrelationIdInTheRequest_forwardsIt() {
        respond(200, "{\"total\": 0}");
        MDC.put(CorrelationIdFilter.MDC_KEY, "6f1c2b7e-3d4a-4b5c-9d6e-7f8a9b0c1d2e");

        client.countActiveAssignments(VEHICLE_ID);

        assertThat(receivedCorrelation.get()).isEqualTo("6f1c2b7e-3d4a-4b5c-9d6e-7f8a9b0c1d2e");
    }

    @Test
    void count_withoutCorrelationId_sendsNoHeader() {
        respond(200, "{\"total\": 0}");

        client.countActiveAssignments(VEHICLE_ID);

        assertThat(receivedCorrelation.get()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 500, 503})
    void count_errorStatus_throwsServiceUnavailable(int errorStatus) {
        respond(errorStatus, "{\"code\": \"ERROR\"}");

        assertThatThrownBy(() -> client.countActiveAssignments(VEHICLE_ID))
                .isInstanceOf(AssignmentServiceUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"content\": []}", "{\"total\": \"1\"}", "{\"total\": 1.5}", "{\"total\": -1}",
        "{\"total\": null}", "{\"total\": 99999999999}", "[]", "null", "not json"})
    void count_responseWithoutValidTotal_throwsServiceUnavailable(String invalidBody) {
        respond(200, invalidBody);

        assertThatThrownBy(() -> client.countActiveAssignments(VEHICLE_ID))
                .isInstanceOf(AssignmentServiceUnavailableException.class);
    }

    @Test
    void count_slowerThanTheTimeout_throwsServiceUnavailable() {
        respond(200, "{\"total\": 0}");
        delayMs = TIMEOUT_MS * 4;

        assertThatThrownBy(() -> client.countActiveAssignments(VEHICLE_ID))
                .isInstanceOf(AssignmentServiceUnavailableException.class);
    }

    @Test
    void count_serviceDown_throwsServiceUnavailable() {
        server.stop(0);

        assertThatThrownBy(() -> client.countActiveAssignments(VEHICLE_ID))
                .isInstanceOf(AssignmentServiceUnavailableException.class);
    }
}
