package co.edu.univalle.fleetops.vehicles.domain.model;

/** Estados operativos del vehículo (catálogo de la sección 3 del documento base). */
public enum VehicleStatus {
    ACTIVE,
    ON_ROUTE,
    BLOCKED,
    IN_MAINTENANCE,
    INACTIVE_EXPIRED_DOCUMENTS,
    DELETED
}
