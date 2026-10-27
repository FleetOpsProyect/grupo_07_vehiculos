package co.edu.univalle.fleetops.vehicles.service;

import co.edu.univalle.fleetops.vehicles.dto.VehicleResponse;
import co.edu.univalle.fleetops.vehicles.exception.FieldErrorDetail;
import co.edu.univalle.fleetops.vehicles.exception.ResourceNotFoundException;
import co.edu.univalle.fleetops.vehicles.exception.ValidationException;
import co.edu.univalle.fleetops.vehicles.model.AppliedRouteModel;
import co.edu.univalle.fleetops.vehicles.model.BlockType;
import co.edu.univalle.fleetops.vehicles.model.VehicleBlockModel;
import co.edu.univalle.fleetops.vehicles.model.VehicleModel;
import co.edu.univalle.fleetops.vehicles.repository.AppliedRouteRepository;
import co.edu.univalle.fleetops.vehicles.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica que reacciona a los eventos de los otros microservicios (ADR-09).
 * Cada evento agrega o quita su propio bloqueo y el estado se recalcula;
 * así un vehículo solo vuelve a ACTIVE cuando no le queda ningún bloqueo.
 * Los consumidores del broker (Sprint 1) solo traducen el evento y llaman a estos métodos.
 */
@Service
public class VehicleStatusService {

    /** Repositorio de vehículos. */
    private final VehicleRepository vehicleRepository;

    /** Rutas ya sumadas al odómetro. */
    private final AppliedRouteRepository appliedRouteRepository;

    /** Recalcula el estado y guarda el historial. */
    private final StatusHistoryRecorder historyRecorder;

    /** Reloj de la aplicación. */
    private final Clock clock;

    /**
     * Constructor con inyección de dependencias.
     * @param vehicleRepository repositorio de vehículos
     * @param appliedRouteRepository rutas aplicadas
     * @param historyRecorder historial de estados
     * @param clock reloj de la aplicación
     */
    public VehicleStatusService(
            final VehicleRepository vehicleRepository,
            final AppliedRouteRepository appliedRouteRepository,
            final StatusHistoryRecorder historyRecorder,
            final Clock clock) {
        this.vehicleRepository = vehicleRepository;
        this.appliedRouteRepository = appliedRouteRepository;
        this.historyRecorder = historyRecorder;
        this.clock = clock;
    }

    /**
     * IncidenteReportadoEvent (G5): bloquea el vehículo si el incidente lo exige.
     * @param vehicleId vehículo del incidente
     * @param incidentId id del incidente
     * @param incidentType tipo de incidente, ej. MECHANICAL_FAILURE
     * @param requiresBlock true si el incidente exige bloquear el vehículo
     * @return vehículo después de aplicar el evento
     */
    @Transactional
    public VehicleResponse applyIncident(
            final UUID vehicleId, final UUID incidentId, final String incidentType, final boolean requiresBlock) {
        VehicleModel vehicle = findVehicle(vehicleId);
        String key = VehicleStatusRules.blockKey(BlockType.INCIDENT, incidentId.toString());
        if (!requiresBlock || vehicle.isDeleted() || vehicle.hasBlock(key)) {
            return VehicleResponse.from(vehicle);
        }
        vehicle.addBlock(newBlock(BlockType.INCIDENT, key, incidentId.toString(), incidentType));
        return saveWithStatus(vehicle, "INCIDENT_REPORTED", incidentId.toString());
    }

    /**
     * MantenimientoFinalizadoEvent (G3): quita el bloqueo de la orden y, si es un
     * correctivo, también el del incidente que lo originó.
     * @param vehicleId vehículo
     * @param maintenanceId orden finalizada
     * @param incidentId incidente asociado (null en un preventivo)
     * @return vehículo después de aplicar el evento
     */
    @Transactional
    public VehicleResponse finishMaintenance(final UUID vehicleId, final UUID maintenanceId, final UUID incidentId) {
        VehicleModel vehicle = findVehicle(vehicleId);
        boolean changed = vehicle.removeBlock(
                VehicleStatusRules.blockKey(BlockType.MAINTENANCE, maintenanceId.toString()));
        if (incidentId != null) {
            changed |= vehicle.removeBlock(VehicleStatusRules.blockKey(BlockType.INCIDENT, incidentId.toString()));
        }
        if (!changed) {
            return VehicleResponse.from(vehicle);
        }
        return saveWithStatus(vehicle, "MAINTENANCE_FINISHED", maintenanceId.toString());
    }

    /**
     * document_expired_event (G1) con owner_type VEHICLE: bloquea por documento vencido.
     * @param vehicleId vehículo dueño del documento
     * @param documentId id del documento
     * @param documentType tipo, ej. SOAT
     * @return vehículo después de aplicar el evento
     */
    @Transactional
    public VehicleResponse markDocumentExpired(
            final UUID vehicleId, final UUID documentId, final String documentType) {
        VehicleModel vehicle = findVehicle(vehicleId);
        String key = VehicleStatusRules.blockKey(BlockType.DOCUMENT, documentType);
        if (vehicle.isDeleted() || vehicle.hasBlock(key)) {
            return VehicleResponse.from(vehicle);
        }
        vehicle.addBlock(newBlock(BlockType.DOCUMENT, key, documentId.toString(), documentType + "_EXPIRED"));
        return saveWithStatus(vehicle, "DOCUMENT_EXPIRED", documentId.toString());
    }

    /**
     * document_renewed_event (G1): quita el bloqueo del tipo de documento renovado.
     * @param vehicleId vehículo dueño del documento
     * @param documentId id del documento nuevo
     * @param documentType tipo, ej. SOAT
     * @return vehículo después de aplicar el evento
     */
    @Transactional
    public VehicleResponse markDocumentRenewed(
            final UUID vehicleId, final UUID documentId, final String documentType) {
        VehicleModel vehicle = findVehicle(vehicleId);
        if (!vehicle.removeBlock(VehicleStatusRules.blockKey(BlockType.DOCUMENT, documentType))) {
            return VehicleResponse.from(vehicle);
        }
        return saveWithStatus(vehicle, "DOCUMENT_RENEWED", documentId.toString());
    }

    /**
     * RutaIniciadaEvent (G6): marca la ruta en curso; sin bloqueos el estado pasa a ON_ROUTE.
     * @param vehicleId vehículo
     * @param routeId ruta iniciada
     * @return vehículo después de aplicar el evento
     */
    @Transactional
    public VehicleResponse startRoute(final UUID vehicleId, final UUID routeId) {
        VehicleModel vehicle = findVehicle(vehicleId);
        if (vehicle.isDeleted() || routeId.equals(vehicle.getActiveRouteId())) {
            return VehicleResponse.from(vehicle);
        }
        vehicle.setActiveRouteId(routeId);
        return saveWithStatus(vehicle, "ROUTE_STARTED", routeId.toString());
    }

    /**
     * RutaFinalizadaEvent (G6), completa o interrumpida: suma la distancia al odómetro
     * una sola vez por ruta y libera la ruta en curso.
     * @param vehicleId vehículo
     * @param routeId ruta finalizada
     * @param distanceKm distancia recorrida
     * @return vehículo después de aplicar el evento
     * @throws ValidationException si la distancia es nula o negativa
     */
    @Transactional
    public VehicleResponse finishRoute(final UUID vehicleId, final UUID routeId, final BigDecimal distanceKm) {
        if (distanceKm == null || distanceKm.signum() < 0) {
            throw new ValidationException(List.of(new FieldErrorDetail("distance_km", "OUT_OF_RANGE")));
        }
        VehicleModel vehicle = findVehicle(vehicleId);
        if (appliedRouteRepository.existsById(routeId)) {
            return VehicleResponse.from(vehicle);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        vehicle.setOdometerKm(vehicle.getOdometerKm().add(distanceKm));
        if (routeId.equals(vehicle.getActiveRouteId())) {
            vehicle.setActiveRouteId(null);
        }
        appliedRouteRepository.save(AppliedRouteModel.builder()
                .routeId(routeId)
                .vehicleId(vehicleId)
                .distanceKm(distanceKm)
                .appliedAt(now)
                .build());
        return saveWithStatus(vehicle, "ROUTE_FINISHED", routeId.toString());
    }

    private VehicleBlockModel newBlock(
            final BlockType type, final String key, final String sourceId, final String reason) {
        return VehicleBlockModel.builder()
                .blockKey(key)
                .blockType(type)
                .sourceId(sourceId)
                .reason(reason)
                .createdAt(OffsetDateTime.now(clock))
                .build();
    }

    private VehicleResponse saveWithStatus(final VehicleModel vehicle, final String cause, final String sourceId) {
        historyRecorder.refreshStatus(vehicle, cause, sourceId);
        vehicle.setUpdatedAt(OffsetDateTime.now(clock));
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    private VehicleModel findVehicle(final UUID id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "VEHICLE_NOT_FOUND", "No existe un vehículo con id " + id));
    }
}
