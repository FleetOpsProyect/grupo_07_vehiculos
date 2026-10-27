package co.edu.univalle.fleetops.vehicles.service;

import static co.edu.univalle.fleetops.vehicles.service.VehicleFixtures.CLOCK;
import static co.edu.univalle.fleetops.vehicles.service.VehicleFixtures.VEHICLE_ID;
import static co.edu.univalle.fleetops.vehicles.service.VehicleFixtures.activeVehicle;
import static co.edu.univalle.fleetops.vehicles.service.VehicleFixtures.validCreateRequest;
import static co.edu.univalle.fleetops.vehicles.service.VehicleFixtures.withBlock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import co.edu.univalle.fleetops.vehicles.client.AssignmentClient;
import co.edu.univalle.fleetops.vehicles.client.DocumentClient;
import co.edu.univalle.fleetops.vehicles.dto.ChangeStatusRequest;
import co.edu.univalle.fleetops.vehicles.dto.CreateVehicleRequest;
import co.edu.univalle.fleetops.vehicles.dto.EligibleVehiclesResponse;
import co.edu.univalle.fleetops.vehicles.dto.PageResponse;
import co.edu.univalle.fleetops.vehicles.dto.UpdateVehicleRequest;
import co.edu.univalle.fleetops.vehicles.dto.VehicleResponse;
import co.edu.univalle.fleetops.vehicles.exception.BusinessException;
import co.edu.univalle.fleetops.vehicles.exception.BusinessRuleException;
import co.edu.univalle.fleetops.vehicles.exception.FieldErrorDetail;
import co.edu.univalle.fleetops.vehicles.exception.ResourceNotFoundException;
import co.edu.univalle.fleetops.vehicles.exception.ServiceUnavailableException;
import co.edu.univalle.fleetops.vehicles.exception.ValidationException;
import co.edu.univalle.fleetops.vehicles.model.BlockType;
import co.edu.univalle.fleetops.vehicles.model.StatusHistoryModel;
import co.edu.univalle.fleetops.vehicles.model.VehicleModel;
import co.edu.univalle.fleetops.vehicles.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.model.VehicleType;
import co.edu.univalle.fleetops.vehicles.repository.StatusCount;
import co.edu.univalle.fleetops.vehicles.repository.StatusHistoryRepository;
import co.edu.univalle.fleetops.vehicles.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringExtension;

// @ExtendWith conecta JUnit 5 con Spring para que los @Mock se inicialicen
@ExtendWith(SpringExtension.class)
class VehicleServiceTest {

    private static final LocalDate VALID_UNTIL = LocalDate.of(2026, 11, 15);

    // Dependencias simuladas: no hay base de datos ni red
    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private StatusHistoryRepository historyRepository;

    @Mock
    private DocumentClient documentClient;

    @Mock
    private AssignmentClient assignmentClient;

    // El Service REAL que vamos a probar, con validador e historial reales
    private VehicleService vehicleService;

    // Conteo por estado para el bloque resumen
    private record Count(VehicleStatus getStatus, Long getTotal) implements StatusCount {
    }

    @BeforeEach
    void setUp() {
        vehicleService = new VehicleService(
                vehicleRepository,
                new VehicleValidator(CLOCK),
                new StatusHistoryRecorder(historyRepository, CLOCK),
                documentClient,
                assignmentClient,
                CLOCK);
    }

    // El repositorio devuelve el mismo vehículo que recibe, con id asignado
    private void givenSaveAssignsId() {
        given(vehicleRepository.save(any(VehicleModel.class))).willAnswer(invocation -> {
            VehicleModel vehicle = invocation.getArgument(0);
            if (vehicle.getId() == null) {
                vehicle.setId(VEHICLE_ID);
            }
            return vehicle;
        });
    }

    private static Set<FieldErrorDetail> errorsOf(final ValidationException ex) {
        return Set.copyOf(ex.getErrors());
    }

    // ── Registrar ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Registrar vehículo: normaliza la placa, queda ACTIVE y guarda el historial")
    void whenCreate_withValidData_thenReturnActiveVehicle() {
        given(vehicleRepository.existsByPlateAndDeletedFalse("ABC123")).willReturn(false);
        givenSaveAssignsId();

        VehicleResponse result = vehicleService.create(validCreateRequest());

        assertEquals("ABC123", result.plate());
        assertEquals(VehicleStatus.ACTIVE, result.status());
        assertEquals(VEHICLE_ID, result.vehicleId());
        assertTrue(result.activeBlocks().isEmpty());
        verify(historyRepository).save(any(StatusHistoryModel.class));
    }

    @Test
    @DisplayName("Registrar vehículo con placa duplicada lanza BusinessException (409)")
    void whenCreate_withDuplicatePlate_thenThrowBusinessException() {
        given(vehicleRepository.existsByPlateAndDeletedFalse("ABC123")).willReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> vehicleService.create(validCreateRequest()));

        assertEquals("PLATE_ALREADY_EXISTS", ex.getCode());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Registrar vehículo vacío reporta todos los campos obligatorios a la vez")
    void whenCreate_withEmptyRequest_thenReportAllRequiredFields() {
        var request = new CreateVehicleRequest(null, null, null, " ", null, null, null, null);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> vehicleService.create(request));

        assertEquals(Set.of(
                new FieldErrorDetail("plate", "REQUIRED"),
                new FieldErrorDetail("vehicle_type", "REQUIRED"),
                new FieldErrorDetail("brand", "REQUIRED"),
                new FieldErrorDetail("model", "REQUIRED"),
                new FieldErrorDetail("year", "REQUIRED"),
                new FieldErrorDetail("odometer_km", "REQUIRED")), errorsOf(ex));
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Registrar vehículo con placa, año y kilometraje fuera de rango")
    void whenCreate_withInvalidValues_thenThrowValidationException() {
        var request = new CreateVehicleRequest("AB1234", VehicleType.CAR, "Mazda", "3",
                2028, "x".repeat(31), "y".repeat(501), new BigDecimal("-1"));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> vehicleService.create(request));

        assertEquals(Set.of(
                new FieldErrorDetail("plate", "INVALID_FORMAT"),
                new FieldErrorDetail("year", "OUT_OF_RANGE"),
                new FieldErrorDetail("color", "TOO_LONG"),
                new FieldErrorDetail("notes", "TOO_LONG"),
                new FieldErrorDetail("odometer_km", "OUT_OF_RANGE")), errorsOf(ex));
    }

    @Test
    @DisplayName("Registrar vehículo con marca muy larga, año antiguo y kilometraje excesivo")
    void whenCreate_withTooLongBrandAndOldYear_thenThrowValidationException() {
        var request = new CreateVehicleRequest("ABC123", VehicleType.CAR, "m".repeat(51), "3",
                1979, null, null, new BigDecimal("2000001"));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> vehicleService.create(request));

        assertEquals(Set.of(
                new FieldErrorDetail("brand", "TOO_LONG"),
                new FieldErrorDetail("year", "OUT_OF_RANGE"),
                new FieldErrorDetail("odometer_km", "OUT_OF_RANGE")), errorsOf(ex));
    }

    // ── Consultar ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Consultar vehículo existente lo retorna con sus bloqueos")
    void whenGetById_withExistingId_thenReturnVehicle() {
        var vehicle = withBlock(activeVehicle(), BlockType.INCIDENT, "inc-1");
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(vehicle));

        VehicleResponse result = vehicleService.getById(VEHICLE_ID);

        assertEquals(VehicleStatus.BLOCKED, result.status());
        assertEquals("INCIDENT:inc-1", result.activeBlocks().get(0).blockKey());
    }

    @Test
    @DisplayName("Consultar vehículo inexistente lanza ResourceNotFoundException (404)")
    void whenGetById_withNonExistingId_thenThrowNotFound() {
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> vehicleService.getById(VEHICLE_ID));

        assertEquals("VEHICLE_NOT_FOUND", ex.getCode());
    }

    // ── Listar ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Listar sin filtros retorna la página y el resumen por estado")
    void whenList_withoutFilters_thenReturnPageWithSummary() {
        given(vehicleRepository.findAllByDeletedFalse(any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(activeVehicle())));
        given(vehicleRepository.countByStatus())
                .willReturn(List.of(new Count(VehicleStatus.ACTIVE, 1L), new Count(VehicleStatus.BLOCKED, 2L)));

        PageResponse<VehicleResponse> result = vehicleService.list(null, null, 0, 20);

        assertEquals(1, result.content().size());
        assertEquals(1L, result.total());
        assertEquals(2L, result.summary().get(VehicleStatus.BLOCKED));
    }

    @Test
    @DisplayName("Listar por estado usa la consulta filtrada por estado")
    void whenList_withStatus_thenFilterByStatus() {
        given(vehicleRepository.findAllByStatusAndDeletedFalse(any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        vehicleService.list(VehicleStatus.ACTIVE, null, 0, 20);

        verify(vehicleRepository).findAllByStatusAndDeletedFalse(any(), any(Pageable.class));
    }

    @Test
    @DisplayName("Listar por tipo usa la consulta filtrada por tipo")
    void whenList_withType_thenFilterByType() {
        given(vehicleRepository.findAllByVehicleTypeAndDeletedFalse(any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        vehicleService.list(null, VehicleType.CAR, 0, 20);

        verify(vehicleRepository).findAllByVehicleTypeAndDeletedFalse(any(), any(Pageable.class));
    }

    @Test
    @DisplayName("Listar por estado y tipo usa la consulta con ambos filtros")
    void whenList_withStatusAndType_thenFilterByBoth() {
        given(vehicleRepository.findAllByStatusAndVehicleTypeAndDeletedFalse(any(), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        vehicleService.list(VehicleStatus.ACTIVE, VehicleType.CAR, 0, 20);

        verify(vehicleRepository).findAllByStatusAndVehicleTypeAndDeletedFalse(any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("Listar con página negativa o tamaño mayor a 100 lanza ValidationException")
    void whenList_withInvalidPage_thenThrowValidationException() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> vehicleService.list(null, null, -1, 101));

        assertEquals(Set.of(
                new FieldErrorDetail("page", "OUT_OF_RANGE"),
                new FieldErrorDetail("size", "OUT_OF_RANGE")), errorsOf(ex));
    }

    // ── Aptos ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Aptos: solo retorna los vehículos que Documentación confirma")
    void whenListEligible_thenReturnOnlyVehiclesWithValidDocuments() {
        var apto = activeVehicle(UUID.randomUUID(), "AAA111");
        var noApto = activeVehicle(UUID.randomUUID(), "BBB222");
        given(vehicleRepository.findAllByStatusAndVehicleTypeAndDeletedFalseOrderByPlateAsc(
                VehicleStatus.ACTIVE, VehicleType.PICKUP_TRUCK)).willReturn(List.of(apto, noApto));
        given(documentClient.findIdsWithValidDocuments(anyList(), any())).willReturn(Set.of(apto.getId()));

        EligibleVehiclesResponse result = vehicleService.listEligible(VehicleType.PICKUP_TRUCK, VALID_UNTIL);

        assertEquals(1, result.total());
        assertEquals("AAA111", result.content().get(0).plate());
        assertFalse(result.truncated());
    }

    @Test
    @DisplayName("Aptos sin candidatos no llama a Documentación")
    void whenListEligible_withoutCandidates_thenDoNotCallDocuments() {
        given(vehicleRepository.findAllByStatusAndDeletedFalseOrderByPlateAsc(VehicleStatus.ACTIVE))
                .willReturn(List.of());

        EligibleVehiclesResponse result = vehicleService.listEligible(null, VALID_UNTIL);

        assertEquals(0, result.total());
        verify(documentClient, never()).findIdsWithValidDocuments(anyList(), any());
    }

    @Test
    @DisplayName("Aptos: lotes de 1000 ids hacia Documentación y máximo 500 en la respuesta")
    void whenListEligible_withMoreThanLimit_thenBatchAndTruncate() {
        List<VehicleModel> candidates = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            candidates.add(activeVehicle(UUID.randomUUID(), String.format("AAA%03d", i % 1000)));
        }
        Set<UUID> allIds = candidates.stream().map(VehicleModel::getId).collect(Collectors.toSet());
        given(vehicleRepository.findAllByStatusAndDeletedFalseOrderByPlateAsc(VehicleStatus.ACTIVE))
                .willReturn(candidates);
        given(documentClient.findIdsWithValidDocuments(anyList(), any())).willReturn(allIds);

        EligibleVehiclesResponse result = vehicleService.listEligible(null, VALID_UNTIL);

        verify(documentClient, times(2)).findIdsWithValidDocuments(anyList(), any());
        assertEquals(1001, result.total());
        assertEquals(VehicleService.ELIGIBLE_LIMIT, result.content().size());
        assertTrue(result.truncated());
    }

    @Test
    @DisplayName("Aptos: si Documentación no responde se propaga 503")
    void whenListEligible_andDocumentsDown_thenThrowServiceUnavailable() {
        given(vehicleRepository.findAllByStatusAndDeletedFalseOrderByPlateAsc(VehicleStatus.ACTIVE))
                .willReturn(List.of(activeVehicle()));
        given(documentClient.findIdsWithValidDocuments(anyList(), any()))
                .willThrow(new ServiceUnavailableException("DOCUMENT_SERVICE_UNAVAILABLE", "caído"));

        assertThrows(ServiceUnavailableException.class,
                () -> vehicleService.listEligible(null, VALID_UNTIL));
    }

    // ── Modificar ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Modificar datos editables de un vehículo existente")
    void whenUpdate_withValidData_thenReturnUpdated() {
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(activeVehicle()));
        givenSaveAssignsId();
        var request = new UpdateVehicleRequest("Toyota", "Hilux SR", 2023, "Gris", "Repintada",
                null, null, null, null);

        VehicleResponse result = vehicleService.update(VEHICLE_ID, request);

        assertEquals("Hilux SR", result.model());
        assertEquals("Gris", result.color());
        assertEquals("ABC123", result.plate());
    }

    @Test
    @DisplayName("Modificar enviando placa, tipo, kilometraje o estado responde NOT_EDITABLE")
    void whenUpdate_withNonEditableFields_thenThrowValidationException() {
        var request = new UpdateVehicleRequest("Toyota", "Hilux", 2023, null, null,
                "XYZ999", VehicleType.CAR, BigDecimal.ONE, VehicleStatus.ACTIVE);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> vehicleService.update(VEHICLE_ID, request));

        assertEquals(4, ex.getErrors().size());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Modificar un vehículo eliminado lanza BusinessException (409)")
    void whenUpdate_withDeletedVehicle_thenThrowBusinessException() {
        var vehicle = activeVehicle();
        vehicle.setDeleted(true);
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(vehicle));
        var request = new UpdateVehicleRequest("Toyota", "Hilux", 2023, null, null, null, null, null, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> vehicleService.update(VEHICLE_ID, request));

        assertEquals("VEHICLE_DELETED", ex.getCode());
    }

    // ── Pasar a mantenimiento ─────────────────────────────────────────────

    @Test
    @DisplayName("Pasar a mantenimiento desde ACTIVE agrega el bloqueo y deja IN_MAINTENANCE")
    void whenChangeToMaintenance_fromActive_thenInMaintenance() {
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(activeVehicle()));
        givenSaveAssignsId();
        UUID maintenanceId = UUID.randomUUID();

        VehicleResponse result = vehicleService.changeToMaintenance(VEHICLE_ID,
                new ChangeStatusRequest(VehicleStatus.IN_MAINTENANCE, maintenanceId, null));

        assertEquals(VehicleStatus.IN_MAINTENANCE, result.status());
        assertEquals("MAINTENANCE:" + maintenanceId, result.activeBlocks().get(0).blockKey());
        assertEquals("PREVENTIVE", result.activeBlocks().get(0).reason());
        verify(historyRepository).save(any(StatusHistoryModel.class));
    }

    @Test
    @DisplayName("Repetir la misma orden de mantenimiento no cambia nada (idempotente)")
    void whenChangeToMaintenance_withSameOrder_thenNoChanges() {
        UUID maintenanceId = UUID.randomUUID();
        var vehicle = withBlock(activeVehicle(), BlockType.MAINTENANCE, maintenanceId.toString());
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(vehicle));

        VehicleResponse result = vehicleService.changeToMaintenance(VEHICLE_ID,
                new ChangeStatusRequest(VehicleStatus.IN_MAINTENANCE, maintenanceId, "Umbral"));

        assertEquals(VehicleStatus.IN_MAINTENANCE, result.status());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Pasar a mantenimiento un vehículo que no está ACTIVE lanza BusinessException (409)")
    void whenChangeToMaintenance_fromBlocked_thenThrowBusinessException() {
        var vehicle = withBlock(activeVehicle(), BlockType.INCIDENT, "inc-1");
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(vehicle));
        var request = new ChangeStatusRequest(VehicleStatus.IN_MAINTENANCE, UUID.randomUUID(), null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> vehicleService.changeToMaintenance(VEHICLE_ID, request));

        assertEquals("INVALID_STATUS_TRANSITION", ex.getCode());
    }

    @Test
    @DisplayName("Pedir un estado distinto de IN_MAINTENANCE lanza BusinessRuleException (422)")
    void whenChangeStatus_toActive_thenThrowBusinessRuleException() {
        var request = new ChangeStatusRequest(VehicleStatus.ACTIVE, UUID.randomUUID(), null);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> vehicleService.changeToMaintenance(VEHICLE_ID, request));

        assertEquals("STATUS_NOT_ALLOWED", ex.getCode());
    }

    @Test
    @DisplayName("Pasar a mantenimiento sin orden y con motivo muy largo lanza ValidationException")
    void whenChangeToMaintenance_withoutOrder_thenThrowValidationException() {
        var request = new ChangeStatusRequest(VehicleStatus.IN_MAINTENANCE, null, "r".repeat(101));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> vehicleService.changeToMaintenance(VEHICLE_ID, request));

        assertEquals(Set.of(
                new FieldErrorDetail("maintenance_id", "REQUIRED"),
                new FieldErrorDetail("reason", "TOO_LONG")), errorsOf(ex));
    }

    // ── Eliminar ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Eliminar un vehículo sin asignaciones activas lo deja DELETED")
    void whenDelete_withoutActiveAssignments_thenDeleted() {
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(activeVehicle()));
        given(assignmentClient.countActiveAssignments(VEHICLE_ID)).willReturn(0L);
        givenSaveAssignsId();

        VehicleResponse result = vehicleService.delete(VEHICLE_ID);

        assertTrue(result.deleted());
        assertEquals(VehicleStatus.DELETED, result.status());
    }

    @Test
    @DisplayName("Eliminar un vehículo con asignaciones activas lanza BusinessException (409)")
    void whenDelete_withActiveAssignments_thenThrowBusinessException() {
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(activeVehicle()));
        given(assignmentClient.countActiveAssignments(VEHICLE_ID)).willReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> vehicleService.delete(VEHICLE_ID));

        assertEquals("VEHICLE_HAS_ACTIVE_ASSIGNMENTS", ex.getCode());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Eliminar un vehículo ya eliminado no consulta Asignaciones (idempotente)")
    void whenDelete_alreadyDeleted_thenReturnWithoutCallingAssignments() {
        var vehicle = activeVehicle();
        vehicle.setDeleted(true);
        vehicle.setStatus(VehicleStatus.DELETED);
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.of(vehicle));

        VehicleResponse result = vehicleService.delete(VEHICLE_ID);

        assertTrue(result.deleted());
        verify(assignmentClient, never()).countActiveAssignments(any());
    }

    @Test
    @DisplayName("Eliminar un vehículo inexistente lanza ResourceNotFoundException (404)")
    void whenDelete_withNonExistingId_thenThrowNotFound() {
        given(vehicleRepository.findById(VEHICLE_ID)).willReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> vehicleService.delete(VEHICLE_ID));
    }

    // ── Normalización de placa ────────────────────────────────────────────

    @Test
    @DisplayName("La placa se normaliza a mayúsculas sin espacios ni guiones")
    void whenNormalizePlate_thenUppercaseWithoutSeparators() {
        assertEquals("ABC123", VehicleValidator.normalizePlate(" abc - 123 "));
        assertNull(VehicleValidator.normalizePlate("   "));
    }
}
