package co.edu.univalle.fleetops.vehicles.domain.rule;

import co.edu.univalle.fleetops.vehicles.domain.model.BlockType;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleBlock;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.util.List;
import java.util.UUID;

/**
 * Cálculo del estado a partir de los bloqueos, con la prioridad del documento base:
 * DELETED &gt; BLOCKED &gt; IN_MAINTENANCE &gt; INACTIVE_EXPIRED_DOCUMENTS &gt; ON_ROUTE &gt; ACTIVE.
 */
public final class VehicleStatusRule {

    private VehicleStatusRule() {
    }

    /**
     * Calcula el estado de un vehículo.
     *
     * @param deleted si tiene baja lógica
     * @param blocks bloqueos activos
     * @param activeRouteId ruta en curso, o nulo
     * @return el estado de mayor prioridad que aplica
     */
    public static VehicleStatus calculate(boolean deleted, List<VehicleBlock> blocks, UUID activeRouteId) {
        if (deleted) {
            return VehicleStatus.DELETED;
        }
        if (hasBlockOfType(blocks, BlockType.INCIDENT)) {
            return VehicleStatus.BLOCKED;
        }
        if (hasBlockOfType(blocks, BlockType.MAINTENANCE)) {
            return VehicleStatus.IN_MAINTENANCE;
        }
        if (hasBlockOfType(blocks, BlockType.DOCUMENT)) {
            return VehicleStatus.INACTIVE_EXPIRED_DOCUMENTS;
        }
        if (activeRouteId != null) {
            return VehicleStatus.ON_ROUTE;
        }
        return VehicleStatus.ACTIVE;
    }

    private static boolean hasBlockOfType(List<VehicleBlock> blocks, BlockType type) {
        return blocks.stream().anyMatch(block -> block.type() == type);
    }
}
