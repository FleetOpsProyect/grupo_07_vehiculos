package co.edu.univalle.fleetops.vehicles.application.event;

import co.edu.univalle.fleetops.vehicles.application.exception.InvalidEventDataException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lee y valida los campos de {@code data} de un evento recibido. Un campo ausente o inválido lanza
 * {@link InvalidEventDataException}, para que el mensaje termine en la DLQ en vez de aplicarse a medias.
 */
public final class EventData {

    /** Largo máximo de un id de texto: es el de {@code source_id} en bloqueos e historial. */
    public static final int MAX_ID_LENGTH = 64;

    private static final Pattern CANONICAL_UUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private EventData() {
    }

    /**
     * Lee un texto obligatorio de hasta {@value #MAX_ID_LENGTH} caracteres.
     *
     * @param data contenido del evento
     * @param field nombre del campo
     * @return el texto
     * @throws InvalidEventDataException si falta, no es texto, está vacío o es muy largo
     */
    public static String text(Map<String, Object> data, String field) {
        if (!(data.get(field) instanceof String value) || value.isBlank()) {
            throw new InvalidEventDataException(field, "is required");
        }
        if (value.length() > MAX_ID_LENGTH) {
            throw new InvalidEventDataException(field, "is longer than " + MAX_ID_LENGTH);
        }
        return value;
    }

    /**
     * Lee un texto opcional: {@code null} o vacío cuenta como ausente.
     *
     * @param data contenido del evento
     * @param field nombre del campo
     * @return el texto, o {@code null}
     * @throws InvalidEventDataException si viene con otro tipo o es muy largo
     */
    public static String optionalText(Map<String, Object> data, String field) {
        Object value = data.get(field);
        if (value == null || value instanceof String text && text.isBlank()) {
            return null;
        }
        return text(data, field);
    }

    /**
     * Lee un UUID obligatorio en formato canónico (8-4-4-4-12).
     *
     * @param data contenido del evento
     * @param field nombre del campo
     * @return el UUID
     * @throws InvalidEventDataException si falta o no es un UUID canónico
     */
    public static UUID uuid(Map<String, Object> data, String field) {
        String value = text(data, field);
        if (!CANONICAL_UUID.matcher(value).matches()) {
            throw new InvalidEventDataException(field, "is not a UUID");
        }
        return UUID.fromString(value);
    }

    /**
     * Lee un número no negativo y lo deja con un decimal, como el odómetro.
     *
     * @param data contenido del evento
     * @param field nombre del campo
     * @return el número con un decimal
     * @throws InvalidEventDataException si falta, no es número o es negativo
     */
    public static BigDecimal nonNegativeDecimal(Map<String, Object> data, String field) {
        if (!(data.get(field) instanceof Number number)) {
            throw new InvalidEventDataException(field, "must be a number");
        }
        BigDecimal value = new BigDecimal(number.toString()).setScale(1, RoundingMode.HALF_UP);
        if (value.signum() < 0) {
            throw new InvalidEventDataException(field, "must not be negative");
        }
        return value;
    }
}
