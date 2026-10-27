package co.edu.univalle.fleetops.vehicles.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import co.edu.univalle.fleetops.vehicles.exception.ServiceUnavailableException;
import co.edu.univalle.fleetops.vehicles.repository.AppliedRouteRepository;
import co.edu.univalle.fleetops.vehicles.repository.StatusHistoryRepository;
import co.edu.univalle.fleetops.vehicles.repository.VehicleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

// Flujo completo HTTP → controller → service → repository → PostgreSQL real
@DisplayName("Tests de integración - Vehículos")
class VehicleIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private StatusHistoryRepository historyRepository;

    @Autowired
    private AppliedRouteRepository appliedRouteRepository;

    // Puerto aleatorio asignado al servidor de pruebas
    @Value("${local.server.port}")
    private int port;

    private String baseUrl() {
        return "http://localhost:" + port + "/vehicles";
    }

    // Cada test parte de una BD limpia
    @BeforeEach
    void limpiarBaseDeDatos() {
        historyRepository.deleteAll();
        appliedRouteRepository.deleteAll();
        vehicleRepository.deleteAll();
    }

    // Cuerpo JSON de registro (snake_case, como lo envía el frontend)
    private Map<String, Object> vehicleBody(final String plate) {
        Map<String, Object> body = new HashMap<>();
        body.put("plate", plate);
        body.put("vehicle_type", "PICKUP_TRUCK");
        body.put("brand", "Toyota");
        body.put("model", "Hilux");
        body.put("year", 2023);
        body.put("color", "Blanco");
        body.put("odometer_km", 15200);
        return body;
    }

    private JsonNode json(final ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    // Registra un vehículo por HTTP y devuelve su id
    private String createVehicle(final String plate) throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl(), vehicleBody(plate), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return json(response).get("vehicle_id").asText();
    }

    private ResponseEntity<String> put(final String url, final Map<String, Object> body) {
        return restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body), String.class);
    }

    private Map<String, Object> maintenanceBody(final String status) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("maintenance_id", UUID.randomUUID().toString());
        return body;
    }

    // ── POST /vehicles ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /vehicles - registra en ACTIVE, normaliza la placa y retorna 201 con Location")
    void whenPostVehicle_thenReturnCreated() throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl(), vehicleBody("abc-123"), String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getHeaders().getLocation());
        JsonNode body = json(response);
        assertEquals("ABC123", body.get("plate").asText());
        assertEquals("ACTIVE", body.get("status").asText());
        assertEquals("PICKUP_TRUCK", body.get("vehicle_type").asText());
    }

    @Test
    @DisplayName("POST /vehicles - retorna 409 PLATE_ALREADY_EXISTS con placa duplicada")
    void whenPostVehicle_withDuplicatePlate_thenReturnConflict() throws Exception {
        createVehicle("ABC123");

        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl(), vehicleBody("abc 123"), String.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("PLATE_ALREADY_EXISTS", json(response).get("code").asText());
    }

    @Test
    @DisplayName("POST /vehicles - retorna 400 con todos los campos inválidos")
    void whenPostVehicle_withInvalidData_thenReturnBadRequest() throws Exception {
        Map<String, Object> body = vehicleBody("1234");
        body.remove("brand");
        body.put("year", 1970);

        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl(), body, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        JsonNode json = json(response);
        assertEquals("VALIDATION_ERROR", json.get("code").asText());
        assertEquals(3, json.get("errors").size());
    }

    // ── GET /vehicles/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /vehicles/{id} - retorna el vehículo con 200")
    void whenGetVehicle_withExistingId_thenReturnVehicle() throws Exception {
        String id = createVehicle("ABC123");

        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl() + "/" + id, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(id, json(response).get("vehicle_id").asText());
    }

    @Test
    @DisplayName("GET /vehicles/{id} - retorna 404 VEHICLE_NOT_FOUND con id inexistente")
    void whenGetVehicle_withUnknownId_thenReturnNotFound() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/" + UUID.randomUUID(), String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("VEHICLE_NOT_FOUND", json(response).get("code").asText());
    }

    @Test
    @DisplayName("GET /vehicles/{id} - retorna 400 si el id no es un UUID")
    void whenGetVehicle_withInvalidId_thenReturnBadRequest() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl() + "/abc", String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ── GET /vehicles ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /vehicles - retorna la página con total y resumen por estado")
    void whenListVehicles_thenReturnPageWithSummary() throws Exception {
        createVehicle("ABC123");
        createVehicle("DEF456");

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "?status=ACTIVE&type=PICKUP_TRUCK&page=0&size=1", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode json = json(response);
        assertEquals(1, json.get("content").size());
        assertEquals(2, json.get("total").asInt());
        assertEquals(2, json.get("summary").get("ACTIVE").asInt());
    }

    @Test
    @DisplayName("GET /vehicles - retorna 400 con size mayor a 100")
    void whenListVehicles_withInvalidSize_thenReturnBadRequest() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl() + "?size=500", String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("GET /vehicles?valid_until - retorna solo los aptos que confirma Documentación")
    void whenListEligible_thenReturnVehiclesWithValidDocuments() throws Exception {
        String apto = createVehicle("ABC123");
        createVehicle("DEF456");
        given(documentClient.findIdsWithValidDocuments(anyList(), any()))
                .willReturn(new HashSet<>(List.of(UUID.fromString(apto))));

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "?type=PICKUP_TRUCK&valid_until=2026-12-31", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode json = json(response);
        assertEquals(1, json.get("total").asInt());
        assertEquals(apto, json.get("content").get(0).get("vehicle_id").asText());
        assertFalse(json.get("truncated").asBoolean());
    }

    @Test
    @DisplayName("GET /vehicles?valid_until - retorna 503 si Documentación no responde")
    void whenListEligible_andDocumentsDown_thenReturnServiceUnavailable() throws Exception {
        createVehicle("ABC123");
        given(documentClient.findIdsWithValidDocuments(anyList(), any()))
                .willThrow(new ServiceUnavailableException("DOCUMENT_SERVICE_UNAVAILABLE", "caído"));

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "?valid_until=2026-12-31", String.class);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DOCUMENT_SERVICE_UNAVAILABLE", json(response).get("code").asText());
    }

    @Test
    @DisplayName("GET /vehicles?valid_until - retorna 400 con fecha mal escrita")
    void whenListEligible_withInvalidDate_thenReturnBadRequest() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "?valid_until=31-12-2026", String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ── PUT /vehicles/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /vehicles/{id} - modifica los datos editables y retorna 200")
    void whenUpdateVehicle_thenReturnUpdated() throws Exception {
        String id = createVehicle("ABC123");
        Map<String, Object> body = new HashMap<>();
        body.put("brand", "Toyota");
        body.put("model", "Hilux SR");
        body.put("year", 2024);
        body.put("color", "Gris");

        ResponseEntity<String> response = put(baseUrl() + "/" + id, body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Hilux SR", json(response).get("model").asText());
    }

    @Test
    @DisplayName("PUT /vehicles/{id} - retorna 400 NOT_EDITABLE si cambia la placa")
    void whenUpdateVehicle_withPlate_thenReturnBadRequest() throws Exception {
        String id = createVehicle("ABC123");
        Map<String, Object> body = new HashMap<>();
        body.put("brand", "Toyota");
        body.put("model", "Hilux");
        body.put("year", 2023);
        body.put("plate", "XYZ999");

        ResponseEntity<String> response = put(baseUrl() + "/" + id, body);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("NOT_EDITABLE", json(response).get("errors").get(0).get("code").asText());
    }

    // ── PUT /vehicles/{id}/status ─────────────────────────────────────────

    @Test
    @DisplayName("PUT /vehicles/{id}/status - pasa a IN_MAINTENANCE y una segunda orden retorna 409")
    void whenChangeToMaintenance_thenInMaintenanceAndSecondOrderConflict() throws Exception {
        String id = createVehicle("ABC123");

        ResponseEntity<String> first = put(baseUrl() + "/" + id + "/status", maintenanceBody("IN_MAINTENANCE"));
        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals("IN_MAINTENANCE", json(first).get("status").asText());

        ResponseEntity<String> second = put(baseUrl() + "/" + id + "/status", maintenanceBody("IN_MAINTENANCE"));
        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
        assertEquals("INVALID_STATUS_TRANSITION", json(second).get("code").asText());
    }

    @Test
    @DisplayName("PUT /vehicles/{id}/status - retorna 422 si piden un estado distinto de IN_MAINTENANCE")
    void whenChangeStatus_toActive_thenReturnUnprocessable() throws Exception {
        String id = createVehicle("ABC123");

        ResponseEntity<String> response = put(baseUrl() + "/" + id + "/status", maintenanceBody("ACTIVE"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
    }

    // ── DELETE /vehicles/{id} ─────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /vehicles/{id} - baja lógica y la placa queda libre para un nuevo registro")
    void whenDeleteVehicle_thenDeletedAndPlateReleased() throws Exception {
        String id = createVehicle("ABC123");
        given(assignmentClient.countActiveAssignments(any())).willReturn(0L);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/" + id, HttpMethod.DELETE, null, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("DELETED", json(response).get("status").asText());
        assertTrue(json(response).get("deleted").asBoolean());
        createVehicle("ABC123");
    }

    @Test
    @DisplayName("DELETE /vehicles/{id} - retorna 409 si tiene asignaciones activas")
    void whenDeleteVehicle_withActiveAssignments_thenReturnConflict() throws Exception {
        String id = createVehicle("ABC123");
        given(assignmentClient.countActiveAssignments(any())).willReturn(2L);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/" + id, HttpMethod.DELETE, null, String.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("VEHICLE_HAS_ACTIVE_ASSIGNMENTS", json(response).get("code").asText());
    }

    @Test
    @DisplayName("DELETE /vehicles/{id} - retorna 503 si Asignaciones no responde")
    void whenDeleteVehicle_andAssignmentsDown_thenReturnServiceUnavailable() throws Exception {
        String id = createVehicle("ABC123");
        given(assignmentClient.countActiveAssignments(any()))
                .willThrow(new ServiceUnavailableException("ASSIGNMENT_SERVICE_UNAVAILABLE", "caído"));

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/" + id, HttpMethod.DELETE, null, String.class);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    // ── Operación ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /health, /ready y /metrics responden 200")
    void whenOperationalEndpoints_thenReturnOk() {
        String root = "http://localhost:" + port;

        assertEquals(HttpStatus.OK, restTemplate.getForEntity(root + "/health", String.class).getStatusCode());
        assertEquals(HttpStatus.OK, restTemplate.getForEntity(root + "/ready", String.class).getStatusCode());
        assertEquals(HttpStatus.OK, restTemplate.getForEntity(root + "/metrics", String.class).getStatusCode());
    }
}
