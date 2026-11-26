package co.edu.univalle.fleetops.vehicles.application.port.out;

import java.util.Map;
import java.util.UUID;

/**
 * Registro de eventos para publicar. Guarda el evento en la misma transacción del caso de uso que lo
 * genera: si esa transacción se revierte, el evento tampoco queda. No conoce SNS.
 */
public interface EventOutbox {

    /**
     * Guarda un evento como pendiente de publicar, dentro de la transacción en curso.
     *
     * @param eventType nombre del evento, por ejemplo {@code vehicle_created_event}
     * @param aggregateId id del vehículo al que pertenece el evento
     * @param data contenido del evento ({@code data} del sobre)
     */
    void record(String eventType, UUID aggregateId, Map<String, Object> data);
}
