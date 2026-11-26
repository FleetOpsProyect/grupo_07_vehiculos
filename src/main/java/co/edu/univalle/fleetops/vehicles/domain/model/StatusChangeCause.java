package co.edu.univalle.fleetops.vehicles.domain.model;

/**
 * Causa registrada en el historial cada vez que cambia el estado o los bloqueos de un vehículo. Las de
 * incidentes, documentos, fin de mantenimiento y rutas llegan por eventos de otros servicios.
 */
public enum StatusChangeCause {
    VEHICLE_CREATED,
    MAINTENANCE_STARTED,
    VEHICLE_DELETED,
    INCIDENT_REPORTED,
    MAINTENANCE_COMPLETED,
    DOCUMENT_EXPIRED,
    DOCUMENT_RENEWED,
    ROUTE_STARTED,
    ROUTE_FINISHED
}
