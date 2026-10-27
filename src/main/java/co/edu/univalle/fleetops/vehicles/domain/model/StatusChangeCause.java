package co.edu.univalle.fleetops.vehicles.domain.model;

/** Causa registrada en el historial cada vez que cambia el estado de un vehículo. */
public enum StatusChangeCause {
    VEHICLE_CREATED,
    MAINTENANCE_STARTED,
    VEHICLE_DELETED
}
