package co.edu.univalle.fleetops.vehicles.model;

/**
 * Catálogo de estados del vehículo.
 * Equivalencias con los acuerdos: ACTIVO, EN_RUTA, BLOQUEADO, EN_MANTENIMIENTO,
 * INACTIVO_POR_DOCUMENTOS e INACTIVO_ELIMINADO.
 */
public enum VehicleStatus {
    /** Disponible para asignar. */
    ACTIVE,
    /** Tiene una ruta en curso. */
    ON_ROUTE,
    /** Tiene un incidente que exige bloqueo. */
    BLOCKED,
    /** Tiene una orden de mantenimiento abierta. */
    IN_MAINTENANCE,
    /** Tiene un documento obligatorio vencido. */
    INACTIVE_EXPIRED_DOCUMENTS,
    /** Baja lógica; estado final. */
    DELETED
}
