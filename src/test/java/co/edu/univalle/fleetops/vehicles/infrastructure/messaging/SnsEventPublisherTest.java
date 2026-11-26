package co.edu.univalle.fleetops.vehicles.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.univalle.fleetops.vehicles.application.event.PendingEvent;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

class SnsEventPublisherTest {

    private static final String ARN = "arn:aws:sns:us-east-1:000000000000:fleetops_vehicle_events";

    private final SnsClient sns = mock(SnsClient.class);
    private final SnsEventPublisher publisher = new SnsEventPublisher(sns, ARN);
    private final PendingEvent event = new PendingEvent(UUID.randomUUID(), "vehicle_created_event",
            "{\"event_type\":\"vehicle_created_event\"}");

    @SuppressWarnings("unchecked")
    @Test
    void publish_sendsTheEnvelopeAsIsWithTheEventTypeAttribute() {
        when(sns.publish(any(Consumer.class))).thenReturn(PublishResponse.builder().build());

        publisher.publish(event);

        ArgumentCaptor<Consumer<PublishRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(sns).publish(captor.capture());
        PublishRequest.Builder builder = PublishRequest.builder();
        captor.getValue().accept(builder);
        PublishRequest request = builder.build();
        assertThat(request.topicArn()).isEqualTo(ARN);
        assertThat(request.message()).isEqualTo(event.payload());
        assertThat(request.messageAttributes()).containsOnlyKeys("event_type");
        assertThat(request.messageAttributes().get("event_type").dataType()).isEqualTo("String");
        assertThat(request.messageAttributes().get("event_type").stringValue()).isEqualTo("vehicle_created_event");
    }

    @SuppressWarnings("unchecked")
    @Test
    void publish_snsFails_theErrorReachesTheCaller() {
        when(sns.publish(any(Consumer.class))).thenThrow(SdkClientException.create("connection refused"));

        assertThatThrownBy(() -> publisher.publish(event)).isInstanceOf(SdkClientException.class);
    }
}
