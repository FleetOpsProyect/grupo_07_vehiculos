package co.edu.univalle.fleetops.vehicles.application.result;

/** Qué pasó con un mensaje recibido; en todos los casos el mensaje se puede borrar de la cola. */
public enum IncomingEventResult {
    /** Era nuevo: se ejecutó su caso de uso y se registró como procesado. */
    PROCESSED,
    /**
     * Era nuevo y se registró como procesado, pero no cambió nada (vehículo desconocido o eliminado, o
     * ya estaba en ese estado).
     */
    IGNORED,
    /** Su {@code event_id} ya estaba procesado: no se cambió nada. */
    DUPLICATE,
    /** Nadie atiende ese {@code event_type}: se ignora. */
    UNKNOWN_TYPE
}
