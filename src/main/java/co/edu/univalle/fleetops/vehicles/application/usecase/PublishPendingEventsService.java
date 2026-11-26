package co.edu.univalle.fleetops.vehicles.application.usecase;

import co.edu.univalle.fleetops.vehicles.application.event.PendingEvent;
import co.edu.univalle.fleetops.vehicles.application.port.in.PublishPendingEventsUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.out.EventPublisher;
import co.edu.univalle.fleetops.vehicles.application.port.out.OutboxQueue;
import co.edu.univalle.fleetops.vehicles.application.port.out.TransactionPort;
import co.edu.univalle.fleetops.vehicles.application.result.PublishBatchResult;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Publica los eventos pendientes del outbox. Toma el lote con bloqueo en una transacción, así dos
 * instancias no publican el mismo evento. Si un evento falla suma un intento, guarda el error y sigue
 * con el siguiente; el evento queda PENDING para la próxima vuelta.
 */
public class PublishPendingEventsService implements PublishPendingEventsUseCase {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final OutboxQueue queue;
    private final EventPublisher publisher;
    private final TransactionPort transaction;
    private final Clock clock;

    /**
     * Crea el caso de uso con sus puertos.
     *
     * @param queue eventos pendientes del outbox
     * @param publisher publicador en el broker
     * @param transaction ejecución transaccional
     * @param clock reloj en GMT-5
     */
    public PublishPendingEventsService(OutboxQueue queue, EventPublisher publisher, TransactionPort transaction,
                                       Clock clock) {
        this.queue = queue;
        this.publisher = publisher;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public PublishBatchResult execute(int batchSize) {
        return transaction.execute(() -> {
            List<PendingEvent> pending = queue.lockPending(batchSize);
            int published = 0;
            int failed = 0;
            for (PendingEvent event : pending) {
                try {
                    publisher.publish(event);
                } catch (RuntimeException error) {
                    queue.markFailed(event.eventId(), describe(error));
                    failed++;
                    continue;
                }
                queue.markPublished(event.eventId(), OffsetDateTime.now(clock));
                published++;
            }
            return new PublishBatchResult(published, failed);
        });
    }

    private static String describe(RuntimeException error) {
        String message = error.getMessage();
        String text = message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : error.getClass().getSimpleName() + ": " + message;
        return text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
    }
}
