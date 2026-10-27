package co.edu.univalle.fleetops.vehicles.repository;

import co.edu.univalle.fleetops.vehicles.model.VehicleStatus;

/** Proyección de Spring Data con el conteo de vehículos por estado. */
public interface StatusCount {

    /**
     * Estado contado.
     * @return estado
     */
    VehicleStatus getStatus();

    /**
     * Cantidad de vehículos en ese estado.
     * @return total
     */
    Long getTotal();
}
