package co.edu.univalle.fleetops.vehicles.application.usecase;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.CLOCK;
import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.univalle.fleetops.vehicles.application.event.PendingEvent;
import co.edu.univalle.fleetops.vehicles.application.port.out.EventPublisher;
import co.edu.univalle.fleetops.vehicles.application.port.out.OutboxQueue;
import co.edu.univalle.fleetops.vehicles.application.result.PublishBatchResult;
import co.edu.univalle.fleetops.vehicles.application.support.PassThroughTransactionPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublishPendingEventsServiceTest {

    @Mock
    private OutboxQueue queue;
    @Mock
    private EventPublisher publisher;

    private PublishPendingEventsService service;

    @BeforeEach
    void setUp() {
        service = new PublishPendingEventsService(queue, publisher, new PassThroughTransactionPort(), CLOCK);
    }

    private static PendingEvent event(String type) {
        return new PendingEvent(UUID.randomUUID(), type, "{\"event_type\":\"" + type + "\"}");
    }

    @Test
    void execute_noPendingEvents_publishesNothing() {
        when(queue.lockPending(50)).thenReturn(List.of());

        PublishBatchResult result = service.execute(50);

        assertThat(result).isEqualTo(new PublishBatchResult(0, 0));
        verifyNoInteractions(publisher);
    }

    @Test
    void execute_pendingEvents_publishesThemInOrderAndMarksThemPublished() {
        PendingEvent first = event("vehicle_created_event");
        PendingEvent second = event("vehicle_deleted_event");
        when(queue.lockPending(50)).thenReturn(List.of(first, second));

        PublishBatchResult result = service.execute(50);

        assertThat(result).isEqualTo(new PublishBatchResult(2, 0));
        InOrder order = inOrder(publisher, queue);
        order.verify(publisher).publish(first);
        order.verify(queue).markPublished(first.eventId(), NOW);
        order.verify(publisher).publish(second);
        order.verify(queue).markPublished(second.eventId(), NOW);
        verify(queue, never()).markFailed(any(), any());
    }

    @Test
    void execute_batchSize_isPassedToTheQueue() {
        when(queue.lockPending(7)).thenReturn(List.of());

        service.execute(7);

        verify(queue).lockPending(7);
    }

    @Test
    void execute_publisherFailsOnOneEvent_keepsItPendingAndPublishesTheOthers() {
        PendingEvent bad = event("vehicle_updated_event");
        PendingEvent good = event("vehicle_deleted_event");
        when(queue.lockPending(50)).thenReturn(List.of(bad, good));
        doThrow(new IllegalStateException("SNS unavailable")).when(publisher).publish(bad);

        PublishBatchResult result = service.execute(50);

        assertThat(result).isEqualTo(new PublishBatchResult(1, 1));
        verify(queue).markFailed(bad.eventId(), "IllegalStateException: SNS unavailable");
        verify(queue, never()).markPublished(eq(bad.eventId()), any());
        verify(publisher).publish(good);
        verify(queue).markPublished(good.eventId(), NOW);
    }

    @Test
    void execute_everyEventFails_countsAllOfThemAsFailed() {
        PendingEvent first = event("vehicle_created_event");
        PendingEvent second = event("vehicle_created_event");
        when(queue.lockPending(50)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("down")).when(publisher).publish(any());

        PublishBatchResult result = service.execute(50);

        assertThat(result).isEqualTo(new PublishBatchResult(0, 2));
        verify(queue, never()).markPublished(any(), any());
    }

    @Test
    void execute_errorWithoutMessage_storesTheExceptionName() {
        PendingEvent bad = event("vehicle_created_event");
        when(queue.lockPending(50)).thenReturn(List.of(bad));
        doThrow(new IllegalStateException()).when(publisher).publish(bad);

        service.execute(50);

        verify(queue).markFailed(bad.eventId(), "IllegalStateException");
    }

    @Test
    void execute_errorWithBlankMessage_storesTheExceptionName() {
        PendingEvent bad = event("vehicle_created_event");
        when(queue.lockPending(50)).thenReturn(List.of(bad));
        doThrow(new IllegalStateException("  ")).when(publisher).publish(bad);

        service.execute(50);

        verify(queue).markFailed(bad.eventId(), "IllegalStateException");
    }

    @Test
    void execute_veryLongError_isTruncatedBeforeSavingIt() {
        PendingEvent bad = event("vehicle_created_event");
        when(queue.lockPending(50)).thenReturn(List.of(bad));
        doThrow(new IllegalStateException("x".repeat(5000))).when(publisher).publish(bad);

        service.execute(50);

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(queue).markFailed(eq(bad.eventId()), error.capture());
        assertThat(error.getValue()).hasSize(1000).startsWith("IllegalStateException: xxx");
    }

    @Test
    void execute_markingAsPublishedFails_propagatesSoTheTransactionRollsBack() {
        PendingEvent event = event("vehicle_created_event");
        when(queue.lockPending(50)).thenReturn(List.of(event));
        doThrow(new IllegalStateException("database down")).when(queue).markPublished(any(), any());

        assertThatThrownBy(() -> service.execute(50)).isInstanceOf(IllegalStateException.class);
    }
}
