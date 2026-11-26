package co.edu.univalle.fleetops.vehicles.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.univalle.fleetops.vehicles.application.event.IncomingEvent;
import co.edu.univalle.fleetops.vehicles.application.port.in.ProcessIncomingEventUseCase;
import co.edu.univalle.fleetops.vehicles.application.result.IncomingEventResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

@SuppressWarnings("unchecked")
class SqsQueueListenerTest {

    private static final String QUEUE = "http://localhost:4566/000000000000/vehicles_queue";

    private final SqsClient sqs = mock(SqsClient.class);
    private final ProcessIncomingEventUseCase useCase = mock(ProcessIncomingEventUseCase.class);
    private final MessagingMetrics metrics = mock(MessagingMetrics.class);
    private SqsQueueListener listener;

    @BeforeEach
    void setUp() {
        listener = new SqsQueueListener(sqs, QUEUE, useCase, new EventJsonMapper(), metrics, 1, 10, 10);
    }

    @AfterEach
    void tearDown() {
        listener.stop();
        MDC.clear();
    }

    private static Message message(String id, String body) {
        return Message.builder().messageId("m-" + id).receiptHandle("rh-" + id).body(body).build();
    }

    private static String body(String eventId, String type, String correlation) {
        String correlationField = correlation == null ? "" : ",\"correlation_id\":\"" + correlation + "\"";
        return "{\"event_id\":\"" + eventId + "\",\"event_type\":\"" + type + "\"" + correlationField
                + ",\"data\":{\"vehicle_id\":\"v-1\"}}";
    }

    private void queueHolds(Message... messages) {
        when(sqs.receiveMessage(any(Consumer.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(messages).build());
    }

    private DeleteMessageRequest deletedRequest() {
        ArgumentCaptor<Consumer<DeleteMessageRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(sqs).deleteMessage(captor.capture());
        DeleteMessageRequest.Builder builder = DeleteMessageRequest.builder();
        captor.getValue().accept(builder);
        return builder.build();
    }

    @ParameterizedTest
    @EnumSource(IncomingEventResult.class)
    void pollOnce_anyOutcomeOfTheUseCase_deletesTheMessageAndCountsItAsConsumed(IncomingEventResult result) {
        queueHolds(message("1", body("evt-1", "severe_incident_reported_event", "corr-1")));
        when(useCase.execute(any(IncomingEvent.class))).thenReturn(result);

        int read = listener.pollOnce();

        assertThat(read).isEqualTo(1);
        DeleteMessageRequest deleted = deletedRequest();
        assertThat(deleted.queueUrl()).isEqualTo(QUEUE);
        assertThat(deleted.receiptHandle()).isEqualTo("rh-1");
        verify(metrics).messageConsumed();
        verify(metrics, never()).consumeError();
    }

    @Test
    void pollOnce_readsFromTheConfiguredQueueWithLongPolling() {
        queueHolds();

        int read = listener.pollOnce();

        assertThat(read).isZero();
        ArgumentCaptor<Consumer<ReceiveMessageRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(sqs).receiveMessage(captor.capture());
        ReceiveMessageRequest.Builder builder = ReceiveMessageRequest.builder();
        captor.getValue().accept(builder);
        ReceiveMessageRequest request = builder.build();
        assertThat(request.queueUrl()).isEqualTo(QUEUE);
        assertThat(request.waitTimeSeconds()).isEqualTo(1);
        assertThat(request.maxNumberOfMessages()).isEqualTo(10);
    }

    @Test
    void pollOnce_useCaseFails_keepsTheMessageInTheQueueAndCountsTheError() {
        queueHolds(message("1", body("evt-1", "document_expired_event", "corr-1")));
        when(useCase.execute(any(IncomingEvent.class))).thenThrow(new IllegalStateException("boom"));

        listener.pollOnce();

        verify(sqs, never()).deleteMessage(any(Consumer.class));
        verify(metrics).consumeError();
        verify(metrics, never()).messageConsumed();
    }

    @Test
    void pollOnce_invalidBody_keepsTheMessageAndNeverCallsTheUseCase() {
        queueHolds(message("1", "not json"));

        listener.pollOnce();

        verify(sqs, never()).deleteMessage(any(Consumer.class));
        verify(metrics).consumeError();
        verify(useCase, never()).execute(any());
    }

    @Test
    void pollOnce_deletingFails_countsTheErrorAndTheMessageComesBack() {
        queueHolds(message("1", body("evt-1", "document_expired_event", "corr-1")));
        when(useCase.execute(any(IncomingEvent.class))).thenReturn(IncomingEventResult.PROCESSED);
        when(sqs.deleteMessage(any(Consumer.class))).thenThrow(SdkClientException.create("timeout"));

        listener.pollOnce();

        verify(metrics).consumeError();
        verify(metrics, never()).messageConsumed();
    }

    @Test
    void pollOnce_oneBadMessage_doesNotStopTheRestOfTheBatch() {
        queueHolds(message("1", "not json"), message("2", body("evt-2", "document_expired_event", "corr-2")));
        when(useCase.execute(any(IncomingEvent.class))).thenReturn(IncomingEventResult.PROCESSED);

        int read = listener.pollOnce();

        assertThat(read).isEqualTo(2);
        verify(metrics).consumeError();
        verify(metrics).messageConsumed();
        assertThat(deletedRequest().receiptHandle()).isEqualTo("rh-2");
    }

    @Test
    void pollOnce_putsTheMessageCorrelationIdInTheLogContextWhileItIsHandledAndRemovesItAfterwards() {
        queueHolds(message("1", body("evt-1", "document_expired_event", "corr-77")));
        List<String> seen = new ArrayList<>();
        when(useCase.execute(any(IncomingEvent.class))).thenAnswer(call -> {
            seen.add(MDC.get("correlation_id"));
            return IncomingEventResult.PROCESSED;
        });

        listener.pollOnce();

        assertThat(seen).containsExactly("corr-77");
        assertThat(MDC.get("correlation_id")).isNull();
    }

    @Test
    void pollOnce_messageWithoutCorrelationId_getsAGeneratedOne() {
        queueHolds(message("1", body("evt-1", "document_expired_event", null)));
        List<String> seen = new ArrayList<>();
        when(useCase.execute(any(IncomingEvent.class))).thenAnswer(call -> {
            seen.add(MDC.get("correlation_id"));
            return IncomingEventResult.PROCESSED;
        });

        listener.pollOnce();

        assertThat(UUID.fromString(seen.get(0))).isNotNull();
    }

    @Test
    void lifecycle_startsAPollingThreadAndStopsIt() {
        queueHolds();

        listener.start();

        assertThat(listener.isRunning()).isTrue();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(
                () -> verify(sqs, atLeastOnce()).receiveMessage(any(Consumer.class)));
        listener.stop();
        assertThat(listener.isRunning()).isFalse();
    }

    @Test
    void lifecycle_readingTheQueueFails_countsTheErrorAndKeepsTrying() {
        when(sqs.receiveMessage(any(Consumer.class))).thenThrow(SdkClientException.create("unreachable"));

        listener.start();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(metrics, atLeastOnce()).consumeError());
        assertThat(listener.isRunning()).isTrue();
    }

    @Test
    void stop_beforeStart_doesNothing() {
        listener.stop();

        assertThat(listener.isRunning()).isFalse();
    }
}
