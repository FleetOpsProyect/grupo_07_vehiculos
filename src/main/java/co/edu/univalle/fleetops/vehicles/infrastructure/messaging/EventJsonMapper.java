package co.edu.univalle.fleetops.vehicles.infrastructure.messaging;

import co.edu.univalle.fleetops.vehicles.application.event.IncomingEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Convierte el sobre a JSON (para el outbox) y los mensajes recibidos a {@link IncomingEvent}. Usa su
 * propio {@link ObjectMapper}: el formato de los eventos es del contrato y no debe cambiar si alguien
 * ajusta el de la API REST.
 */
@Component
public class EventJsonMapper {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

    /**
     * Escribe el sobre como JSON.
     *
     * @param envelope sobre del evento
     * @return el JSON del sobre
     */
    public String toJson(EventEnvelope envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The event cannot be written as JSON", e);
        }
    }

    /**
     * Lee el cuerpo de un mensaje de la cola. Acepta el sobre tal cual (entrega sin envoltorio) y,
     * por si la suscripción no la tiene activada, también la notificación de SNS que lo trae en
     * {@code Message}.
     *
     * @param body cuerpo del mensaje
     * @return el evento recibido
     * @throws InvalidEventMessageException si no es JSON o le faltan {@code event_id} o {@code event_type}
     */
    public IncomingEvent parse(String body) {
        JsonNode root = read(body);
        if ("Notification".equals(root.path("Type").asText()) && root.path("Message").isTextual()) {
            root = read(root.path("Message").asText());
        }
        String eventId = text(root, "event_id");
        String eventType = text(root, "event_type");
        if (eventId == null || eventType == null) {
            throw new InvalidEventMessageException("The message has no event_id or event_type");
        }
        JsonNode data = root.path("data");
        Map<String, Object> content = data.isObject() ? mapper.convertValue(data, MAP) : Map.of();
        return new IncomingEvent(eventId, eventType, text(root, "correlation_id"), content);
    }

    private JsonNode read(String body) {
        try {
            JsonNode node = mapper.readTree(body);
            if (node == null || !node.isObject()) {
                throw new InvalidEventMessageException("The message is not a JSON object");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new InvalidEventMessageException("The message is not valid JSON", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() && !value.asText().isBlank() ? value.asText() : null;
    }
}
