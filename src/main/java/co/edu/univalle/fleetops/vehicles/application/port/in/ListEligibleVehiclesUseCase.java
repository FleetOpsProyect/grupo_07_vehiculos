package co.edu.univalle.fleetops.vehicles.application.port.in;

import co.edu.univalle.fleetops.vehicles.application.query.ListEligibleVehiclesQuery;
import co.edu.univalle.fleetops.vehicles.application.result.EligibleVehiclesResult;

/** Vehículos aptos para asignar (GET /vehicles?valid_until=...). */
public interface ListEligibleVehiclesUseCase {

    /**
     * Ejecuta el caso de uso.
     *
     * @param query tipo y fecha de vigencia
     * @return los vehículos ACTIVE con documentos vigentes
     */
    EligibleVehiclesResult execute(ListEligibleVehiclesQuery query);
}
