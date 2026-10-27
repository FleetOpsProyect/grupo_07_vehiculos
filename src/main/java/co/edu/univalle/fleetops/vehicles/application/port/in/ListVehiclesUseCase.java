package co.edu.univalle.fleetops.vehicles.application.port.in;

import co.edu.univalle.fleetops.vehicles.application.query.ListVehiclesQuery;
import co.edu.univalle.fleetops.vehicles.application.result.VehiclePageResult;

/** Listado paginado (GET /vehicles). */
public interface ListVehiclesUseCase {

    /**
     * Ejecuta el caso de uso.
     *
     * @param query filtros y paginación
     * @return la página con el resumen por estado
     */
    VehiclePageResult execute(ListVehiclesQuery query);
}
