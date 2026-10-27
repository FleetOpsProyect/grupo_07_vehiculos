package co.edu.univalle.fleetops.vehicles.service;

import co.edu.univalle.fleetops.vehicles.model.BlockType;
import co.edu.univalle.fleetops.vehicles.model.VehicleModel;
import co.edu.univalle.fleetops.vehicles.model.VehicleStatus;

/**
 * Regla de derivación del estado (ADR-09): el estado no se asigna a mano,
 * se calcula a partir de los bloqueos activos. Se evalúa en este orden.
 */
public final class VehicleStatusRules {

    private VehicleStatusRules() {
    }

    /**
     * Calcula el estado que corresponde al vehículo.
     * Ejemplo: con un incidente y el SOAT vencido queda BLOCKED; al renovar el SOAT
     * sigue BLOCKED hasta que se cierre el incidente.
     * @param vehicle vehículo con sus bloqueos cargados
     * @return estado derivado
     */
    public static VehicleStatus derive(final VehicleModel vehicle) {
        if (vehicle.isDeleted()) {
            return VehicleStatus.DELETED;
        }
        if (vehicle.hasBlockOfType(BlockType.INCIDENT)) {
            return VehicleStatus.BLOCKED;
        }
        if (vehicle.hasBlockOfType(BlockType.MAINTENANCE)) {
            return VehicleStatus.IN_MAINTENANCE;
        }
        if (vehicle.hasBlockOfType(BlockType.DOCUMENT)) {
            return VehicleStatus.INACTIVE_EXPIRED_DOCUMENTS;
        }
        if (vehicle.getActiveRouteId() != null) {
            return VehicleStatus.ON_ROUTE;
        }
        return VehicleStatus.ACTIVE;
    }

    /**
     * Construye la clave determinista de un bloqueo.
     * @param blockType tipo de bloqueo
     * @param sourceId id del incidente u orden, o tipo de documento
     * @return clave, ej. INCIDENT:9d2f...
     */
    public static String blockKey(final BlockType blockType, final String sourceId) {
        return blockType.name() + ":" + sourceId;
    }
}
