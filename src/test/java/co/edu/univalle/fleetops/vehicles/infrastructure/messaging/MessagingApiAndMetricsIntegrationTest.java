package co.edu.univalle.fleetops.vehicles.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.univalle.fleetops.vehicles.application.port.out.EventOutbox;
import co.edu.univalle.fleetops.vehicles.application.port.out.TransactionPort;
import co.edu.univalle.fleetops.vehicles.infrastructure.support.LocalStackSupport;
import co.edu.univalle.fleetops.vehicles.infrastructure.support.MessagingIntegrationTest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Con SNS caído la API responde con normalidad, y las métricas de mensajería aparecen en /metrics. */
class MessagingApiAndMetricsIntegrationTest extends MessagingIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private EventOutbox outbox;
    @Autowired
    private TransactionPort transaction;
    @Autowired
    private JdbcTemplate jdbc;

    private String metrics() throws Exception {
        return mvc.perform(get("/metrics")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
    }

    @Test
    void metrics_exposeTheFourMessagingMetrics() throws Exception {
        String body = metrics();

        assertThat(body).contains("vehicles_outbox_pending", "vehicles_outbox_published_total",
                "vehicles_messages_consumed_total", "vehicles_messaging_errors_total{operation=\"publish\"",
                "vehicles_messaging_errors_total{operation=\"consume\"");
    }

    @Test
    void snsDown_theApiKeepsAnsweringAndTheMetricsShowThePendingEventsAndTheErrors() throws Exception {
        LocalStackSupport.deleteTopic();
        try {
            transaction.execute(() -> {
                outbox.record("vehicle_created_event", UUID.randomUUID(), Map.of());
                return null;
            });
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                    "SELECT attempts FROM outbox_events", Integer.class)).isGreaterThanOrEqualTo(1));

            mvc.perform(get("/vehicles")).andExpect(status().isOk());
            mvc.perform(get("/ready")).andExpect(status().isOk());
            String body = metrics();

            assertThat(body).contains("vehicles_outbox_pending 1.0");
            assertThat(body).doesNotContain("vehicles_messaging_errors_total{operation=\"publish\"} 0.0");
        } finally {
            LocalStackSupport.restoreTopic();
        }
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(metrics())
                .contains("vehicles_outbox_pending 0.0"));
    }
}
