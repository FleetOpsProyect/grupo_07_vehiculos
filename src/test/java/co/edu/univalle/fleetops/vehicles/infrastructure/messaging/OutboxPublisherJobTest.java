package co.edu.univalle.fleetops.vehicles.infrastructure.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.univalle.fleetops.vehicles.application.port.in.PublishPendingEventsUseCase;
import co.edu.univalle.fleetops.vehicles.application.result.PublishBatchResult;
import org.junit.jupiter.api.Test;

class OutboxPublisherJobTest {

    private final PublishPendingEventsUseCase useCase = mock(PublishPendingEventsUseCase.class);
    private final MessagingMetrics metrics = mock(MessagingMetrics.class);
    private final OutboxPublisherJob job = new OutboxPublisherJob(useCase, metrics, 50);

    @Test
    void publishPending_countsPublishedAndFailedEvents() {
        when(useCase.execute(50)).thenReturn(new PublishBatchResult(3, 2));

        job.publishPending();

        verify(metrics).eventsPublished(3);
        verify(metrics).publishErrors(2);
    }

    @Test
    void publishPending_nothingFailed_recordsNoErrors() {
        when(useCase.execute(50)).thenReturn(new PublishBatchResult(4, 0));

        job.publishPending();

        verify(metrics).eventsPublished(4);
        verify(metrics).publishErrors(0);
    }

    @Test
    void publishPending_theRoundFails_isCountedAndDoesNotPropagate() {
        when(useCase.execute(50)).thenThrow(new IllegalStateException("database down"));

        job.publishPending();

        verify(metrics).publishErrors(1);
        verify(metrics, never()).eventsPublished(org.mockito.ArgumentMatchers.anyInt());
    }
}
