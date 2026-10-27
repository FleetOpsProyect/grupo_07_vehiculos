package co.edu.univalle.fleetops.vehicles.application.query;

import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import java.time.LocalDate;

/**
 * Filtros de GET /vehicles?valid_until=... (vehículos aptos para asignar).
 *
 * @param type filtro opcional por tipo
 * @param validUntil fecha hasta la que deben estar vigentes los documentos
 */
public record ListEligibleVehiclesQuery(VehicleType type, LocalDate validUntil) {
}
