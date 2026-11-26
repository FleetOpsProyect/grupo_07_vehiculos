package co.edu.univalle.fleetops.vehicles.application.port.in;

import co.edu.univalle.fleetops.vehicles.application.event.IncomingEvent;
import co.edu.univalle.fleetops.vehicles.application.result.IncomingEventResult;

/** Aplica un mensaje recibido de la cola una sola vez. */
public interface ProcessIncomingEventUseCase {

    /**
     * Ejecuta el caso de uso del evento y lo registra como procesado, todo en una transacción.
     *
     * @param event mensaje recibido
     * @return qué se hizo con el mensaje
     */
    IncomingEventResult execute(IncomingEvent event);
}
