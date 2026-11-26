package co.edu.univalle.fleetops.vehicles.infrastructure.messaging;

import co.edu.univalle.fleetops.vehicles.application.port.in.PublishPendingEventsUseCase;
import co.edu.univalle.fleetops.vehicles.application.result.PublishBatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Cada 2 s publica los eventos pendientes del outbox. Corre en el hilo del planificador, nunca en el de
 * una petición HTTP: si SNS o la base de datos fallan, la API sigue respondiendo.
 */
public class OutboxPublisherJob {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisherJob.class);

    private final PublishPendingEventsUseCase publisher;
    private final MessagingMetrics metrics;
    private final int batchSize;

    /**
     * Crea el job.
     *
     * @param publisher caso de uso que publica un lote
     * @param metrics métricas de la mensajería
     * @param batchSize eventos por vuelta
     */
    public OutboxPublisherJob(PublishPendingEventsUseCase publisher, MessagingMetrics metrics, int batchSize) {
        this.publisher = publisher;
        this.metrics = metrics;
        this.batchSize = batchSize;
    }

    /** Una vuelta del publicador; no lanza excepciones para que el planificador siga con la siguiente. */
    @Scheduled(fixedDelayString = "${fleetops.messaging.publisher.fixed-delay-ms:2000}")
    public void publishPending() {
        try {
            PublishBatchResult result = publisher.execute(batchSize);
            metrics.eventsPublished(result.published());
            metrics.publishErrors(result.failed());
            if (result.failed() > 0) {
                LOG.warn("Outbox: {} event(s) could not be published and stay PENDING", result.failed());
            }
        } catch (RuntimeException error) {
            metrics.publishErrors(1);
            LOG.warn("Outbox publisher round failed: {}", error.toString());
        }
    }
}
