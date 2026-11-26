package co.edu.univalle.fleetops.vehicles.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class MessagingMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private MessagingMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new MessagingMetrics(registry, jdbc);
    }

    @Test
    void counters_startAtZeroAndAddUp() {
        metrics.eventsPublished(3);
        metrics.eventsPublished(2);
        metrics.messageConsumed();
        metrics.publishErrors(4);
        metrics.consumeError();

        assertThat(registry.get("vehicles.outbox.published").counter().count()).isEqualTo(5);
        assertThat(registry.get("vehicles.messages.consumed").counter().count()).isEqualTo(1);
        assertThat(registry.get("vehicles.messaging.errors").tag("operation", "publish").counter().count())
                .isEqualTo(4);
        assertThat(registry.get("vehicles.messaging.errors").tag("operation", "consume").counter().count())
                .isEqualTo(1);
    }

    @Test
    void pendingGauge_readsTheOutboxEachTime() {
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class))).thenReturn(7L, 0L);

        assertThat(registry.get("vehicles.outbox.pending").gauge().value()).isEqualTo(7);
        assertThat(registry.get("vehicles.outbox.pending").gauge().value()).isZero();
    }

    @Test
    void pendingGauge_nullCount_isZero() {
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class))).thenReturn(null);

        assertThat(registry.get("vehicles.outbox.pending").gauge().value()).isZero();
    }

    @Test
    void pendingGauge_databaseDown_isNotANumberInsteadOfFailingTheScrape() {
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class)))
                .thenThrow(new DataAccessResourceFailureException("down"));

        assertThat(registry.get("vehicles.outbox.pending").gauge().value()).isNaN();
    }
}
