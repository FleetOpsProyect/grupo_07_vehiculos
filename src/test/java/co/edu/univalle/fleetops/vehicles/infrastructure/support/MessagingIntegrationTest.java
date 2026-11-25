package co.edu.univalle.fleetops.vehicles.infrastructure.support;

import co.edu.univalle.fleetops.vehicles.application.event.IncomingEvent;
import co.edu.univalle.fleetops.vehicles.application.port.in.IncomingEventHandler;
import co.edu.univalle.fleetops.vehicles.application.port.out.EventOutbox;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base de las pruebas de mensajería: PostgreSQL y LocalStack reales, con el publicador del outbox y el
 * listener de {@code vehicles_queue} funcionando como en producción (pero con esperas cortas). Registra
 * dos manejadores de prueba: {@code test_ok_event} y {@code test_failing_event}.
 *
 * <p>Todas las clases de mensajería comparten un único contexto de Spring (por eso las anotaciones están
 * aquí y no en cada clase): un segundo contexto arrancaría otro listener sobre la misma cola y le quitaría
 * mensajes al primero. {@code @AutoConfigureObservability} activa el exportador de Prometheus, que las
 * pruebas de Spring Boot desactivan por defecto.
 */
@AutoConfigureMockMvc
@AutoConfigureObservability
@Import(MessagingIntegrationTest.TestHandlers.class)
public abstract class MessagingIntegrationTest extends PostgresIntegrationTest {

    public static final String OK_EVENT = "test_ok_event";
    public static final String FAILING_EVENT = "test_failing_event";

    /** Mensajes que llegaron a los manejadores de prueba, en orden. */
    protected static final List<IncomingEvent> HANDLED = new CopyOnWriteArrayList<>();

    @DynamicPropertySource
    static void messagingProperties(DynamicPropertyRegistry registry) {
        registry.add("fleetops.messaging.enabled", () -> "true");
        registry.add("fleetops.messaging.endpoint", LocalStackSupport::endpoint);
        registry.add("fleetops.messaging.region", LocalStackSupport::region);
        registry.add("fleetops.messaging.topic-arn", LocalStackSupport::topicArn);
        registry.add("fleetops.messaging.queue-url", LocalStackSupport::queueUrl);
        registry.add("fleetops.messaging.publisher.fixed-delay-ms", () -> "200");
        registry.add("fleetops.messaging.listener.wait-time-seconds", () -> "1");
        registry.add("fleetops.messaging.listener.error-backoff-ms", () -> "200");
    }

    @BeforeEach
    void cleanMessaging() {
        LocalStackSupport.purgeQueues();
        HANDLED.clear();
    }

    /** Manejadores de prueba: dejan un evento en el outbox y, el segundo, falla después de dejarlo. */
    @TestConfiguration
    static class TestHandlers {

        @Bean
        IncomingEventHandler okHandler(EventOutbox outbox) {
            return new IncomingEventHandler() {
                @Override
                public String eventType() {
                    return OK_EVENT;
                }

                @Override
                public void handle(IncomingEvent event) {
                    HANDLED.add(event);
                    outbox.record("vehicle_status_changed_event", UUID.randomUUID(),
                            Map.of("source_event_id", event.eventId()));
                }
            };
        }

        @Bean
        IncomingEventHandler failingHandler(EventOutbox outbox) {
            return new IncomingEventHandler() {
                @Override
                public String eventType() {
                    return FAILING_EVENT;
                }

                @Override
                public void handle(IncomingEvent event) {
                    HANDLED.add(event);
                    outbox.record("vehicle_status_changed_event", UUID.randomUUID(),
                            Map.of("source_event_id", event.eventId()));
                    throw new IllegalStateException("handler failed on purpose");
                }
            };
        }
    }
}
