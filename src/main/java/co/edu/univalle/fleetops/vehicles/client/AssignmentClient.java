package co.edu.univalle.fleetops.vehicles.client;

import co.edu.univalle.fleetops.vehicles.exception.ServiceUnavailableException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente HTTP de Asignaciones (G2).
 * Contrato del catálogo: GET /assignment/active, listado de asignaciones PENDIENTE y EN_CURSO.
 */
@Component
public class AssignmentClient {

    /** Cliente HTTP con la URL base de Asignaciones. */
    private final RestClient restClient;

    /**
     * Crea el cliente. El timeout de 2 s se configura en application.yml.
     * @param builder builder autoconfigurado por Spring Boot
     * @param baseUrl URL base de Asignaciones
     */
    public AssignmentClient(
            final RestClient.Builder builder,
            @Value("${fleetops.services.assignments.url}") final String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /**
     * Cuenta las asignaciones pendientes o en curso de un vehículo.
     * @param vehicleId id del vehículo
     * @return cantidad de asignaciones activas
     * @throws ServiceUnavailableException si Asignaciones no responde o responde con error
     */
    public long countActiveAssignments(final UUID vehicleId) {
        try {
            ActiveAssignmentsResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/assignment/active")
                            .queryParam("vehicle_id", vehicleId)
                            .build())
                    .retrieve()
                    .body(ActiveAssignmentsResponse.class);
            if (response == null || response.total() == null) {
                return 0;
            }
            return response.total();
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("ASSIGNMENT_SERVICE_UNAVAILABLE",
                    "No fue posible consultar las asignaciones del vehículo; intente más tarde");
        }
    }

    /**
     * Respuesta paginada de Asignaciones; solo se usa el total. Pendiente de confirmar con G2.
     * @param total cantidad de asignaciones activas
     */
    public record ActiveAssignmentsResponse(Long total) {
    }
}
