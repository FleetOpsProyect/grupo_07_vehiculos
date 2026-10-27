package co.edu.univalle.fleetops.vehicles.application.command;

import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.util.Objects;
import java.util.UUID;

/**
 * Datos de PUT /vehicles/{id}/status.
 *
 * @param vehicleId vehículo a cambiar
 * @param status estado pedido; solo se acepta IN_MAINTENANCE
 * @param maintenanceId id del mantenimiento que origina el bloqueo, obligatorio
 */
public record ChangeToMaintenanceCommand(UUID vehicleId, VehicleStatus status, UUID maintenanceId) {

    /** Valida que el id del mantenimiento exista, porque forma la clave del bloqueo. */
    public ChangeToMaintenanceCommand {
        Objects.requireNonNull(maintenanceId, "maintenanceId");
    }
}
