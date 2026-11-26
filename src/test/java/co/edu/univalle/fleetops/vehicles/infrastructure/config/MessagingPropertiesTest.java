package co.edu.univalle.fleetops.vehicles.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class MessagingPropertiesTest {

    private static MessagingProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("fleetops.messaging", MessagingProperties.class);
    }

    @Test
    void defaults_areEnabledButWithoutTopicOrQueueSoNothingStarts() {
        MessagingProperties properties = bind(Map.of());

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.publishingEnabled()).isFalse();
        assertThat(properties.listeningEnabled()).isFalse();
        assertThat(properties.publisher().batchSize()).isEqualTo(50);
        assertThat(properties.publisher().fixedDelayMs()).isEqualTo(2000);
        assertThat(properties.listener().maxMessages()).isEqualTo(10);
        assertThat(properties.listener().waitTimeSeconds()).isEqualTo(20);
        assertThat(properties.region()).isEqualTo("us-east-1");
    }

    @Test
    void topicAndQueueConfigured_enableEachSideByItself() {
        MessagingProperties onlyTopic = bind(Map.of("fleetops.messaging.topic-arn", "arn:topic"));
        MessagingProperties onlyQueue = bind(Map.of("fleetops.messaging.queue-url", "http://queue"));

        assertThat(onlyTopic.publishingEnabled()).isTrue();
        assertThat(onlyTopic.listeningEnabled()).isFalse();
        assertThat(onlyQueue.publishingEnabled()).isFalse();
        assertThat(onlyQueue.listeningEnabled()).isTrue();
    }

    @Test
    void disabled_turnsOffBothSidesEvenWithTopicAndQueue() {
        MessagingProperties properties = bind(Map.of("fleetops.messaging.enabled", "false",
                "fleetops.messaging.topic-arn", "arn:topic", "fleetops.messaging.queue-url", "http://queue"));

        assertThat(properties.publishingEnabled()).isFalse();
        assertThat(properties.listeningEnabled()).isFalse();
    }
}
