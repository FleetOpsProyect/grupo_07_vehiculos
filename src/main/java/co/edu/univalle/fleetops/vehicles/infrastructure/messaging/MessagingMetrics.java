package co.edu.univalle.fleetops.vehicles.infrastructure.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Métricas de la mensajería para Prometheus ({@code GET /metrics}, puerto 18001 en producción):
 * <ul>
 *   <li>{@code vehicles_outbox_pending}: eventos PENDING en el outbox (gauge);</li>
 *   <li>{@code vehicles_outbox_published_total}: eventos publicados en SNS;</li>
 *   <li>{@code vehicles_messages_consumed_total}: mensajes de SQS atendidos y borrados de la cola;</li>
 *   <li>{@code vehicles_messaging_errors_total{operation=publish|consume}}: errores al publicar o consumir.</li>
 * </ul>
 */
@Component
public class MessagingMetrics {

    private final JdbcTemplate jdbc;
    private final Counter published;
    private final Counter consumed;
    private final Counter publishErrors;
    private final Counter consumeErrors;

    /**
     * Registra las métricas.
     *
     * @param registry registro de Micrometer
     * @param jdbc acceso a la base, para contar los pendientes en cada lectura
     */
    public MessagingMetrics(MeterRegistry registry, JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.published = Counter.builder("vehicles.outbox.published")
                .description("Events published to SNS").register(registry);
        this.consumed = Counter.builder("vehicles.messages.consumed")
                .description("Messages taken from SQS and deleted from the queue").register(registry);
        this.publishErrors = Counter.builder("vehicles.messaging.errors").tag("operation", "publish")
                .description("Errors while publishing or consuming").register(registry);
        this.consumeErrors = Counter.builder("vehicles.messaging.errors").tag("operation", "consume")
                .description("Errors while publishing or consuming").register(registry);
        Gauge.builder("vehicles.outbox.pending", this, MessagingMetrics::pendingEvents)
                .description("Events waiting in the outbox").register(registry);
    }

    /**
     * Suma eventos publicados.
     *
     * @param count cantidad publicada en la vuelta
     */
    public void eventsPublished(int count) {
        published.increment(count);
    }

    /**
     * Suma errores al publicar.
     *
     * @param count cantidad de errores
     */
    public void publishErrors(int count) {
        publishErrors.increment(count);
    }

    /** Suma un mensaje consumido. */
    public void messageConsumed() {
        consumed.increment();
    }

    /** Suma un error al consumir. */
    public void consumeError() {
        consumeErrors.increment();
    }

    private double pendingEvents() {
        try {
            Long pending = jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE status = 'PENDING'",
                    Long.class);
            return pending == null ? 0 : pending;
        } catch (RuntimeException e) {
            // Sin base de datos la lectura de /metrics no debe fallar
            return Double.NaN;
        }
    }
}
