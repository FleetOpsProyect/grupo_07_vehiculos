package co.edu.univalle.fleetops.vehicles.application.port.in;

import co.edu.univalle.fleetops.vehicles.application.event.IncomingEvent;

/** Atiende un tipo de evento recibido de la cola. Cada consumidor del contrato implementa uno. */
public interface IncomingEventHandler {

    /**
     * Tipo de evento que atiende.
     *
     * @return el {@code event_type}, por ejemplo {@code severe_incident_reported_event}
     */
    String eventType();

    /**
     * Aplica el evento. Se ejecuta dentro de la transacción del consumo: si lanza una excepción no
     * queda ningún cambio y el mensaje se reintenta.
     *
     * @param event evento recibido
     */
    void handle(IncomingEvent event);
}
