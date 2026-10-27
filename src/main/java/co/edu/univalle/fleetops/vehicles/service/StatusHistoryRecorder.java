package co.edu.univalle.fleetops.vehicles.service;

import co.edu.univalle.fleetops.vehicles.model.StatusHistoryModel;
import co.edu.univalle.fleetops.vehicles.model.VehicleModel;
import co.edu.univalle.fleetops.vehicles.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.repository.StatusHistoryRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

/**
 * Recalcula el estado del vehículo y deja la traza en el historial cuando cambia.
 * Lo usan tanto las operaciones REST como la lógica de eventos.
 */
@Component
public class StatusHistoryRecorder {

    /** Repositorio del historial. */
    private final StatusHistoryRepository historyRepository;

    /** Reloj de la aplicación. */
    private final Clock clock;

    /**
     * Constructor con inyección de dependencias.
     * @param historyRepository repositorio del historial
     * @param clock reloj de la aplicación
     */
    public StatusHistoryRecorder(final StatusHistoryRepository historyRepository, final Clock clock) {
        this.historyRepository = historyRepository;
        this.clock = clock;
    }

    /**
     * Registra el estado inicial de un vehículo recién creado.
     * @param vehicle vehículo ya guardado (con id)
     */
    public void recordCreation(final VehicleModel vehicle) {
        save(vehicle, null, vehicle.getStatus(), "VEHICLE_CREATED", null);
    }

    /**
     * Recalcula el estado; si cambió, lo actualiza en el vehículo y guarda el historial.
     * @param vehicle vehículo con sus bloqueos ya modificados
     * @param cause causa del cambio, ej. INCIDENT_REPORTED
     * @param sourceId id del recurso o evento que lo causó
     * @return true si el estado cambió
     */
    public boolean refreshStatus(final VehicleModel vehicle, final String cause, final String sourceId) {
        VehicleStatus previous = vehicle.getStatus();
        VehicleStatus current = VehicleStatusRules.derive(vehicle);
        if (previous == current) {
            return false;
        }
        vehicle.setStatus(current);
        save(vehicle, previous, current, cause, sourceId);
        return true;
    }

    private void save(
            final VehicleModel vehicle,
            final VehicleStatus from,
            final VehicleStatus to,
            final String cause,
            final String sourceId) {
        historyRepository.save(StatusHistoryModel.builder()
                .vehicleId(vehicle.getId())
                .fromStatus(from)
                .toStatus(to)
                .cause(cause)
                .sourceId(sourceId)
                .changedAt(OffsetDateTime.now(clock))
                .build());
    }
}
