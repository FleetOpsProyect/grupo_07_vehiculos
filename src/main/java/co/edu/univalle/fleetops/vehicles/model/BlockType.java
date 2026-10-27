package co.edu.univalle.fleetops.vehicles.model;

/** Origen de un bloqueo del vehículo. */
public enum BlockType {
    /** Incidente reportado por Incidentes (G5). */
    INCIDENT,
    /** Orden de mantenimiento de Mantenimiento (G3). */
    MAINTENANCE,
    /** Documento vencido según Documentación (G1). */
    DOCUMENT
}
