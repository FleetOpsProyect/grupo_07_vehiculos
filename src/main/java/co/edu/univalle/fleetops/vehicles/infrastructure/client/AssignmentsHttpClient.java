package co.edu.univalle.fleetops.vehicles.infrastructure.client;

import co.edu.univalle.fleetops.vehicles.application.exception.AssignmentServiceUnavailableException;
import co.edu.univalle.fleetops.vehicles.application.port.out.ActiveAssignmentsPort;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.filter.CorrelationIdFilter;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente HTTP de Asignaciones (G2): {@code GET /assignment/active?vehicle_id={id}} (sección 6 del
 * contrato). Solo lee {@code total}, el número de asignaciones PENDING e IN_PROGRESS. Cualquier falla
 * (sin conexión, más de 2 s, respuesta distinta de 2xx o sin un {@code total} válido) se trata como
 * servicio no disponible, para no borrar un vehículo sin confirmar. Reenvía el {@code X-Correlation-Id}.
 */
@Component
public class AssignmentsHttpClient implements ActiveAssignmentsPort {

    private static final Logger LOG = LoggerFactory.getLogger(AssignmentsHttpClient.class);
    private static final String PATH = "/assignment/active";

    private final RestClient restClient;

    /**
     * Crea el cliente con la URL base de Asignaciones.
     *
     * @param builder constructor de {@link RestClient} de Spring Boot
     * @param baseUrl URL de Asignaciones, por ejemplo {@code http://10.0.0.5:8082}
     * @param timeoutMs tiempo máximo para conectar y, aparte, para leer la respuesta
     */
    public AssignmentsHttpClient(RestClient.Builder builder,
                                 @Value("${fleetops.services.assignments.url}") String baseUrl,
                                 @Value("${fleetops.services.assignments.timeout-ms:2000}") long timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public int countActiveAssignments(UUID vehicleId) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        try {
            JsonNode body = restClient.get()
                    .uri(uri -> uri.path(PATH).queryParam("vehicle_id", vehicleId).build())
                    .headers(headers -> {
                        if (correlationId != null) {
                            headers.set(CorrelationIdFilter.HEADER, correlationId);
                        }
                    })
                    .retrieve()
                    .body(JsonNode.class);
            return readTotal(body);
        } catch (RestClientException | IllegalStateException error) {
            LOG.warn("Assignments service could not be checked for vehicle {}: {}", vehicleId, error.toString());
            throw new AssignmentServiceUnavailableException(error);
        }
    }

    private static int readTotal(JsonNode body) {
        JsonNode total = body == null ? null : body.get("total");
        if (total == null || !total.isIntegralNumber() || !total.canConvertToInt() || total.intValue() < 0) {
            throw new IllegalStateException("Assignments response without a valid total");
        }
        return total.intValue();
    }
}
