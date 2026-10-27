package co.edu.univalle.fleetops.vehicles.client;

import co.edu.univalle.fleetops.vehicles.exception.ServiceUnavailableException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente HTTP de Documentación y Seguros (G1).
 * Contrato del catálogo: POST /documentos/validar-vigencia con
 * {entidad_tipo, entidad_ids[], vigente_hasta}; máximo 1000 ids por llamada.
 */
@Component
public class DocumentClient {

    /** Tipo de entidad que se valida. */
    private static final String VEHICLE = "VEHICLE";

    /** Cliente HTTP con la URL base de Documentación. */
    private final RestClient restClient;

    /**
     * Crea el cliente. El timeout de 2 s se configura en application.yml.
     * @param builder builder autoconfigurado por Spring Boot (usa el ObjectMapper en snake_case)
     * @param baseUrl URL base de Documentación
     */
    public DocumentClient(
            final RestClient.Builder builder,
            @Value("${fleetops.services.documents.url}") final String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /**
     * Pregunta a Documentación cuáles vehículos tienen los documentos obligatorios
     * vigentes hasta la fecha dada.
     * @param vehicleIds ids de los candidatos (máximo 1000)
     * @param validUntil fecha hasta la que deben estar vigentes
     * @return ids de los vehículos con documentos vigentes
     * @throws ServiceUnavailableException si Documentación no responde o responde con error
     */
    public Set<UUID> findIdsWithValidDocuments(final List<UUID> vehicleIds, final LocalDate validUntil) {
        try {
            ValidityResponse response = restClient.post()
                    .uri("/documentos/validar-vigencia")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ValidityRequest(VEHICLE, vehicleIds, validUntil))
                    .retrieve()
                    .body(ValidityResponse.class);
            if (response == null || response.idsVigentes() == null) {
                return Set.of();
            }
            return new HashSet<>(response.idsVigentes());
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("DOCUMENT_SERVICE_UNAVAILABLE",
                    "No fue posible validar la vigencia documental; intente más tarde");
        }
    }

    /**
     * Cuerpo de la petición (en JSON: entidad_tipo, entidad_ids, vigente_hasta).
     * @param entidadTipo VEHICLE
     * @param entidadIds ids a validar
     * @param vigenteHasta fecha límite
     */
    public record ValidityRequest(String entidadTipo, List<UUID> entidadIds, LocalDate vigenteHasta) {
    }

    /**
     * Respuesta esperada (en JSON: ids_vigentes). Nombre pendiente de confirmar con G1.
     * @param idsVigentes ids con documentos vigentes
     */
    public record ValidityResponse(List<UUID> idsVigentes) {
    }
}
