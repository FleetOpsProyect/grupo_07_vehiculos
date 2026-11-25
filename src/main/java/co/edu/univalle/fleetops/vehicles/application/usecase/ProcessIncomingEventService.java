package co.edu.univalle.fleetops.vehicles.application.usecase;

import co.edu.univalle.fleetops.vehicles.application.event.IncomingEvent;
import co.edu.univalle.fleetops.vehicles.application.port.in.IncomingEventHandler;
import co.edu.univalle.fleetops.vehicles.application.port.in.ProcessIncomingEventUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.out.ProcessedEventRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.TransactionPort;
import co.edu.univalle.fleetops.vehicles.application.result.IncomingEventResult;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aplica un mensaje recibido una sola vez. En una sola transacción registra el {@code event_id}
 * como procesado y ejecuta el manejador de su tipo (que puede dejar eventos en el outbox). Si el
 * {@code event_id} ya estaba registrado no ejecuta nada. Si el manejador falla, la transacción se
 * revierte completa y la excepción sube para que el mensaje no se borre de la cola.
 */
public class ProcessIncomingEventService implements ProcessIncomingEventUseCase {

    private final Map<String, IncomingEventHandler> handlers = new HashMap<>();
    private final ProcessedEventRepository processed;
    private final TransactionPort transaction;
    private final Clock clock;

    /**
     * Crea el caso de uso con los manejadores de cada tipo de evento.
     *
     * @param handlers manejadores; no puede haber dos para el mismo tipo
     * @param processed registro de mensajes ya procesados
     * @param transaction ejecución transaccional
     * @param clock reloj en GMT-5
     * @throws IllegalArgumentException si dos manejadores atienden el mismo tipo
     */
    public ProcessIncomingEventService(List<IncomingEventHandler> handlers, ProcessedEventRepository processed,
                                       TransactionPort transaction, Clock clock) {
        for (IncomingEventHandler handler : handlers) {
            if (this.handlers.put(handler.eventType(), handler) != null) {
                throw new IllegalArgumentException("Two handlers for " + handler.eventType());
            }
        }
        this.processed = processed;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public IncomingEventResult execute(IncomingEvent event) {
        IncomingEventHandler handler = handlers.get(event.eventType());
        if (handler == null) {
            return IncomingEventResult.UNKNOWN_TYPE;
        }
        return transaction.execute(() -> {
            if (!processed.markProcessed(event.eventId(), event.eventType(), OffsetDateTime.now(clock))) {
                return IncomingEventResult.DUPLICATE;
            }
            handler.handle(event);
            return IncomingEventResult.PROCESSED;
        });
    }
}
