package co.edu.univalle.fleetops.vehicles.service;

import co.edu.univalle.fleetops.vehicles.client.AssignmentClient;
import co.edu.univalle.fleetops.vehicles.client.DocumentClient;
import co.edu.univalle.fleetops.vehicles.dto.ChangeStatusRequest;
import co.edu.univalle.fleetops.vehicles.dto.CreateVehicleRequest;
import co.edu.univalle.fleetops.vehicles.dto.EligibleVehiclesResponse;
import co.edu.univalle.fleetops.vehicles.dto.PageResponse;
import co.edu.univalle.fleetops.vehicles.dto.UpdateVehicleRequest;
import co.edu.univalle.fleetops.vehicles.dto.VehicleResponse;
import co.edu.univalle.fleetops.vehicles.exception.BusinessException;
import co.edu.univalle.fleetops.vehicles.exception.ResourceNotFoundException;
import co.edu.univalle.fleetops.vehicles.model.BlockType;
import co.edu.univalle.fleetops.vehicles.model.VehicleBlockModel;
import co.edu.univalle.fleetops.vehicles.model.VehicleModel;
import co.edu.univalle.fleetops.vehicles.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.model.VehicleType;
import co.edu.univalle.fleetops.vehicles.repository.VehicleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio de los endpoints REST del MS Vehículos:
 * registro, consulta, listados, aptos, edición, paso a mantenimiento y baja lógica.
 */
@Service
public class VehicleService {

    /** Máximo de aptos que se devuelven sin paginar (acuerdo con Asignaciones). */
    static final int ELIGIBLE_LIMIT = 500;

    /** Máximo de ids por llamada a Documentación (acuerdo con G1). */
    static final int DOCUMENTS_BATCH_SIZE = 1000;

    /** Repositorio de vehículos. */
    private final VehicleRepository vehicleRepository;

    /** Validaciones de entrada. */
    private final VehicleValidator validator;

    /** Recalcula el estado y guarda el historial. */
    private final StatusHistoryRecorder historyRecorder;

    /** Cliente de Documentación (G1). */
    private final DocumentClient documentClient;

    /** Cliente de Asignaciones (G2). */
    private final AssignmentClient assignmentClient;

    /** Reloj de la aplicación. */
    private final Clock clock;

    /**
     * Constructor con inyección de dependencias.
     * @param vehicleRepository repositorio de vehículos
     * @param validator validaciones de entrada
     * @param historyRecorder historial de estados
     * @param documentClient cliente de Documentación
     * @param assignmentClient cliente de Asignaciones
     * @param clock reloj de la aplicación
     */
    public VehicleService(
            final VehicleRepository vehicleRepository,
            final VehicleValidator validator,
            final StatusHistoryRecorder historyRecorder,
            final DocumentClient documentClient,
            final AssignmentClient assignmentClient,
            final Clock clock) {
        this.vehicleRepository = vehicleRepository;
        this.validator = validator;
        this.historyRecorder = historyRecorder;
        this.documentClient = documentClient;
        this.assignmentClient = assignmentClient;
        this.clock = clock;
    }

    /**
     * Registra un vehículo en estado ACTIVE. No llama a Documentación:
     * los documentos se registran después con el id devuelto.
     * @param request datos del vehículo
     * @return vehículo creado
     * @throws BusinessException si la placa ya existe (409)
     */
    @Transactional
    public VehicleResponse create(final CreateVehicleRequest request) {
        String plate = validator.validateCreate(request);
        if (vehicleRepository.existsByPlateAndDeletedFalse(plate)) {
            throw new BusinessException("PLATE_ALREADY_EXISTS", "Ya existe un vehículo con la placa " + plate);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        VehicleModel vehicle = VehicleModel.builder()
                .plate(plate)
                .vehicleType(request.vehicleType())
                .brand(request.brand().trim())
                .model(request.model().trim())
                .year(request.year())
                .color(request.color())
                .notes(request.notes())
                .odometerKm(request.odometerKm())
                .status(VehicleStatus.ACTIVE)
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
        VehicleModel saved = vehicleRepository.save(vehicle);
        historyRecorder.recordCreation(saved);
        return VehicleResponse.from(saved);
    }

    /**
     * Consulta un vehículo por id, incluidos los eliminados. No llama a Documentación.
     * @param id id del vehículo
     * @return vehículo
     * @throws ResourceNotFoundException si no existe (404)
     */
    @Transactional(readOnly = true)
    public VehicleResponse getById(final UUID id) {
        return VehicleResponse.from(findVehicle(id));
    }

    /**
     * Lista los vehículos no eliminados con filtros opcionales y bloque resumen por estado.
     * @param status estado (opcional)
     * @param type tipo (opcional)
     * @param page página, desde 0
     * @param size tamaño de página, de 1 a 100
     * @return página de vehículos
     */
    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> list(
            final VehicleStatus status, final VehicleType type, final int page, final int size) {
        validator.validatePage(page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt"));
        Page<VehicleModel> result = findPage(status, type, pageable);
        Map<VehicleStatus, Long> summary = new EnumMap<>(VehicleStatus.class);
        vehicleRepository.countByStatus().forEach(count -> summary.put(count.getStatus(), count.getTotal()));
        List<VehicleResponse> content = result.getContent().stream().map(VehicleResponse::from).toList();
        return new PageResponse<>(content, page, size, result.getTotalElements(), summary);
    }

    /**
     * Lista los vehículos aptos para una asignación: ACTIVE, del tipo pedido y con
     * documentos vigentes hasta la fecha. Hace una llamada a Documentación por cada
     * lote de 1000 candidatos y devuelve como máximo 500, sin paginar.
     * @param type tipo de vehículo (opcional)
     * @param validUntil fecha hasta la que deben estar vigentes los documentos
     * @return vehículos aptos
     */
    @Transactional(readOnly = true)
    public EligibleVehiclesResponse listEligible(final VehicleType type, final LocalDate validUntil) {
        List<VehicleModel> candidates = type == null
                ? vehicleRepository.findAllByStatusAndDeletedFalseOrderByPlateAsc(VehicleStatus.ACTIVE)
                : vehicleRepository.findAllByStatusAndVehicleTypeAndDeletedFalseOrderByPlateAsc(
                        VehicleStatus.ACTIVE, type);
        if (candidates.isEmpty()) {
            return new EligibleVehiclesResponse(List.of(), 0, false);
        }
        List<UUID> ids = candidates.stream().map(VehicleModel::getId).toList();
        Set<UUID> validIds = new HashSet<>();
        for (int from = 0; from < ids.size(); from += DOCUMENTS_BATCH_SIZE) {
            List<UUID> batch = ids.subList(from, Math.min(from + DOCUMENTS_BATCH_SIZE, ids.size()));
            validIds.addAll(documentClient.findIdsWithValidDocuments(batch, validUntil));
        }
        List<VehicleResponse> eligible = candidates.stream()
                .filter(vehicle -> validIds.contains(vehicle.getId()))
                .map(VehicleResponse::from)
                .toList();
        boolean truncated = eligible.size() > ELIGIBLE_LIMIT;
        List<VehicleResponse> content = truncated ? eligible.subList(0, ELIGIBLE_LIMIT) : eligible;
        return new EligibleVehiclesResponse(content, eligible.size(), truncated);
    }

    /**
     * Modifica los datos editables (marca, modelo, año, color y observaciones).
     * @param id id del vehículo
     * @param request datos nuevos
     * @return vehículo actualizado
     * @throws ResourceNotFoundException si no existe (404)
     * @throws BusinessException si el vehículo está eliminado (409)
     */
    @Transactional
    public VehicleResponse update(final UUID id, final UpdateVehicleRequest request) {
        validator.validateUpdate(request);
        VehicleModel vehicle = findVehicle(id);
        if (vehicle.isDeleted()) {
            throw new BusinessException("VEHICLE_DELETED", "El vehículo " + id + " está eliminado");
        }
        vehicle.setBrand(request.brand().trim());
        vehicle.setModel(request.model().trim());
        vehicle.setYear(request.year());
        vehicle.setColor(request.color());
        vehicle.setNotes(request.notes());
        vehicle.setUpdatedAt(OffsetDateTime.now(clock));
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    /**
     * Pasa el vehículo a IN_MAINTENANCE agregando el bloqueo MAINTENANCE:id.
     * Lo usa el scheduler de Mantenimiento. Repetir la llamada con la misma orden no cambia nada.
     * @param id id del vehículo
     * @param request estado pedido y orden de mantenimiento
     * @return vehículo actualizado
     * @throws BusinessException si el vehículo no estaba ACTIVE (409)
     */
    @Transactional
    public VehicleResponse changeToMaintenance(final UUID id, final ChangeStatusRequest request) {
        validator.validateStatusChange(request);
        VehicleModel vehicle = findVehicle(id);
        String maintenanceId = request.maintenanceId().toString();
        String blockKey = VehicleStatusRules.blockKey(BlockType.MAINTENANCE, maintenanceId);
        if (vehicle.hasBlock(blockKey)) {
            return VehicleResponse.from(vehicle);
        }
        if (vehicle.getStatus() != VehicleStatus.ACTIVE) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    "El vehículo está en " + vehicle.getStatus() + "; solo pasa a mantenimiento desde ACTIVE");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        vehicle.addBlock(VehicleBlockModel.builder()
                .blockKey(blockKey)
                .blockType(BlockType.MAINTENANCE)
                .sourceId(maintenanceId)
                .reason(request.reason() == null ? "PREVENTIVE" : request.reason())
                .createdAt(now)
                .build());
        historyRecorder.refreshStatus(vehicle, "MAINTENANCE_SCHEDULED", maintenanceId);
        vehicle.setUpdatedAt(now);
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    /**
     * Baja lógica. Pregunta a Asignaciones si el vehículo tiene asignaciones pendientes
     * o en curso; si no tiene, lo marca DELETED y libera la placa.
     * @param id id del vehículo
     * @return vehículo eliminado
     * @throws ResourceNotFoundException si no existe (404)
     * @throws BusinessException si tiene asignaciones activas (409)
     */
    @Transactional
    public VehicleResponse delete(final UUID id) {
        VehicleModel vehicle = findVehicle(id);
        if (vehicle.isDeleted()) {
            return VehicleResponse.from(vehicle);
        }
        long activeAssignments = assignmentClient.countActiveAssignments(id);
        if (activeAssignments > 0) {
            throw new BusinessException("VEHICLE_HAS_ACTIVE_ASSIGNMENTS",
                    "El vehículo tiene " + activeAssignments + " asignaciones pendientes o en curso");
        }
        vehicle.setDeleted(true);
        historyRecorder.refreshStatus(vehicle, "VEHICLE_DELETED", null);
        vehicle.setUpdatedAt(OffsetDateTime.now(clock));
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    private VehicleModel findVehicle(final UUID id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "VEHICLE_NOT_FOUND", "No existe un vehículo con id " + id));
    }

    private Page<VehicleModel> findPage(
            final VehicleStatus status, final VehicleType type, final Pageable pageable) {
        if (status != null && type != null) {
            return vehicleRepository.findAllByStatusAndVehicleTypeAndDeletedFalse(status, type, pageable);
        }
        if (status != null) {
            return vehicleRepository.findAllByStatusAndDeletedFalse(status, pageable);
        }
        if (type != null) {
            return vehicleRepository.findAllByVehicleTypeAndDeletedFalse(type, pageable);
        }
        return vehicleRepository.findAllByDeletedFalse(pageable);
    }
}
