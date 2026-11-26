package co.edu.univalle.fleetops.vehicles.infrastructure.messaging;

import co.edu.univalle.fleetops.vehicles.application.event.IncomingEvent;
import co.edu.univalle.fleetops.vehicles.application.port.in.ProcessIncomingEventUseCase;
import co.edu.univalle.fleetops.vehicles.application.result.IncomingEventResult;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.filter.CorrelationIdFilter;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.SmartLifecycle;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Lee {@code vehicles_queue} con long polling en un hilo propio. Un mensaje solo se borra de la cola
 * cuando el caso de uso terminó bien (o era un duplicado o de un tipo desconocido). Si algo falla no se
 * borra: SQS lo entrega de nuevo y, al tercer fallo, lo mueve a {@code vehicles_queue_dlq} según la
 * política de la cola.
 */
public class SqsQueueListener implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(SqsQueueListener.class);
    private static final long STOP_WAIT_MS = 2000;

    private final SqsClient sqs;
    private final String queueUrl;
    private final ProcessIncomingEventUseCase useCase;
    private final EventJsonMapper json;
    private final MessagingMetrics metrics;
    private final int waitTimeSeconds;
    private final int maxMessages;
    private final long errorBackoffMs;
    private volatile boolean running;
    private Thread thread;

    /**
     * Crea el listener.
     *
     * @param sqs cliente de SQS
     * @param queueUrl URL de {@code vehicles_queue}
     * @param useCase caso de uso que aplica cada mensaje una sola vez
     * @param json lector del sobre
     * @param metrics métricas de la mensajería
     * @param waitTimeSeconds espera máxima de cada lectura (long polling)
     * @param maxMessages mensajes por lectura (1 a 10)
     * @param errorBackoffMs pausa tras un error al leer la cola
     */
    public SqsQueueListener(SqsClient sqs, String queueUrl, ProcessIncomingEventUseCase useCase,
                            EventJsonMapper json, MessagingMetrics metrics, int waitTimeSeconds,
                            int maxMessages, long errorBackoffMs) {
        this.sqs = sqs;
        this.queueUrl = queueUrl;
        this.useCase = useCase;
        this.json = json;
        this.metrics = metrics;
        this.waitTimeSeconds = waitTimeSeconds;
        this.maxMessages = maxMessages;
        this.errorBackoffMs = errorBackoffMs;
    }

    @Override
    public void start() {
        running = true;
        thread = new Thread(this::pollWhileRunning, "sqs-listener");
        thread.setDaemon(true);
        thread.start();
        LOG.info("Listening to the vehicles queue");
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(STOP_WAIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void pollWhileRunning() {
        while (running) {
            try {
                pollOnce();
            } catch (RuntimeException error) {
                if (!running) {
                    return;
                }
                metrics.consumeError();
                LOG.warn("Could not read the vehicles queue: {}", error.toString());
                pause();
            }
        }
    }

    private void pause() {
        try {
            Thread.sleep(errorBackoffMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    /**
     * Lee un lote de la cola y atiende cada mensaje.
     *
     * @return cuántos mensajes leyó
     */
    int pollOnce() {
        List<Message> messages = sqs.receiveMessage(request -> request.queueUrl(queueUrl)
                .maxNumberOfMessages(maxMessages).waitTimeSeconds(waitTimeSeconds)).messages();
        messages.forEach(this::handle);
        return messages.size();
    }

    private void handle(Message message) {
        try {
            IncomingEvent event = json.parse(message.body());
            MDC.put(CorrelationIdFilter.MDC_KEY,
                    event.correlationId() != null ? event.correlationId() : UUID.randomUUID().toString());
            IncomingEventResult result = useCase.execute(event);
            logResult(event, result);
            sqs.deleteMessage(request -> request.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
            metrics.messageConsumed();
        } catch (RuntimeException error) {
            metrics.consumeError();
            LOG.warn("Message {} was not processed and stays in the queue: {}", message.messageId(),
                    error.toString().replaceAll("[\r\n]", "_"));
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private static void logResult(IncomingEvent event, IncomingEventResult result) {
        String type = event.eventType().replaceAll("[\r\n]", "_");
        String id = event.eventId().replaceAll("[\r\n]", "_");
        switch (result) {
            case UNKNOWN_TYPE -> LOG.warn("Unknown event_type {} (event_id {}): message discarded", type, id);
            case DUPLICATE -> LOG.info("Event {} ({}) was already processed: message discarded", id, type);
            case PROCESSED -> LOG.info("Event {} ({}) processed", id, type);
            default -> throw new IllegalStateException("Unexpected result " + result);
        }
    }
}
