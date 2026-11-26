package co.edu.univalle.fleetops.vehicles.application.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.univalle.fleetops.vehicles.application.exception.InvalidEventDataException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventDataTest {

    private static Map<String, Object> data(String field, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put(field, value);
        return data;
    }

    @Test
    void text_present_returnsIt() {
        assertThat(EventData.text(data("incident_id", "inc-1"), "incident_id")).isEqualTo("inc-1");
    }

    @Test
    void text_missingBlankOrNotText_isInvalid() {
        assertThatThrownBy(() -> EventData.text(Map.of(), "incident_id"))
                .isInstanceOf(InvalidEventDataException.class).hasMessageContaining("incident_id is required");
        assertThatThrownBy(() -> EventData.text(data("incident_id", " "), "incident_id"))
                .isInstanceOf(InvalidEventDataException.class);
        assertThatThrownBy(() -> EventData.text(data("incident_id", 15), "incident_id"))
                .isInstanceOf(InvalidEventDataException.class);
    }

    @Test
    void text_longerThanTheLimit_isInvalid() {
        Map<String, Object> data = data("document_id", "x".repeat(EventData.MAX_ID_LENGTH + 1));

        assertThatThrownBy(() -> EventData.text(data, "document_id"))
                .isInstanceOf(InvalidEventDataException.class).hasMessageContaining("longer than 64");
    }

    @Test
    void optionalText_nullOrBlank_returnsNullAndAValueIsReturned() {
        assertThat(EventData.optionalText(data("incident_id", null), "incident_id")).isNull();
        assertThat(EventData.optionalText(data("incident_id", ""), "incident_id")).isNull();
        assertThat(EventData.optionalText(data("incident_id", "inc-1"), "incident_id")).isEqualTo("inc-1");
    }

    @Test
    void optionalText_otherType_isInvalid() {
        Map<String, Object> data = data("incident_id", 7);

        assertThatThrownBy(() -> EventData.optionalText(data, "incident_id"))
                .isInstanceOf(InvalidEventDataException.class);
    }

    @Test
    void uuid_canonical_returnsIt() {
        UUID id = UUID.randomUUID();

        assertThat(EventData.uuid(data("vehicle_id", id.toString()), "vehicle_id")).isEqualTo(id);
    }

    @Test
    void uuid_notCanonical_isInvalid() {
        Map<String, Object> data = data("vehicle_id", "1-2-3-4-5");

        assertThatThrownBy(() -> EventData.uuid(data, "vehicle_id"))
                .isInstanceOf(InvalidEventDataException.class).hasMessageContaining("not a UUID");
    }

    @Test
    void nonNegativeDecimal_numbers_areRoundedToOneDecimal() {
        assertThat(EventData.nonNegativeDecimal(data("distance_km", 6.25), "distance_km"))
                .isEqualByComparingTo("6.3").hasScaleOf(1);
        assertThat(EventData.nonNegativeDecimal(data("distance_km", 0), "distance_km"))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void nonNegativeDecimal_missingTextOrNegative_isInvalid() {
        assertThatThrownBy(() -> EventData.nonNegativeDecimal(Map.of(), "distance_km"))
                .isInstanceOf(InvalidEventDataException.class).hasMessageContaining("must be a number");
        assertThatThrownBy(() -> EventData.nonNegativeDecimal(data("distance_km", "6.2"), "distance_km"))
                .isInstanceOf(InvalidEventDataException.class);
        assertThatThrownBy(() -> EventData.nonNegativeDecimal(data("distance_km", -0.5), "distance_km"))
                .isInstanceOf(InvalidEventDataException.class).hasMessageContaining("negative");
    }
}
