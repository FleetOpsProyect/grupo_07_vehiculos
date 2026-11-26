package co.edu.univalle.fleetops.vehicles.application.port.out;

import co.edu.univalle.fleetops.vehicles.application.event.PendingEvent;

/** Publica un evento en el broker. */
public interface EventPublisher {

    /**
     * Publica el evento. Si el broker no lo acepta lanza una excepción.
     *
     * @param event evento a publicar
     */
    void publish(PendingEvent event);
}
