package co.edu.univalle.fleetops.vehicles.infrastructure.messaging;

import co.edu.univalle.fleetops.vehicles.infrastructure.web.filter.CorrelationIdFilter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Arma el sobre de cada evento: UUID nuevo, hora en GMT-5, {@code source = vehicle} y el
 * {@code correlation_id} de la petición o del mensaje que se está atendiendo (el filtro y el listener
 * lo dejan en el MDC). Si no hay ninguno genera un UUID, como pide el contrato.
 */
@Component
public class EventEnvelopeFactory {

    private final Clock clock;

    /**
     * Crea la fábrica.
     *
     * @param clock reloj en GMT-5
     */
    public EventEnvelopeFactory(Clock clock) {
        this.clock = clock;
    }

    /**
     * Crea el sobre de un evento nuevo.
     *
     * @param eventType nombre del evento
     * @param data contenido del evento
     * @return el sobre listo para guardar en el outbox
     */
    public EventEnvelope create(String eventType, Map<String, Object> data) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        return new EventEnvelope(UUID.randomUUID(), eventType,
                OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS), EventEnvelope.SOURCE, correlationId,
                data);
    }
}
