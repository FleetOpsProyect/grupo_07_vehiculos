package co.edu.univalle.fleetops.vehicles.application.usecase;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.CLOCK;
import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.univalle.fleetops.vehicles.application.event.IncomingEvent;
import co.edu.univalle.fleetops.vehicles.application.port.in.IncomingEventHandler;
import co.edu.univalle.fleetops.vehicles.application.port.out.ProcessedEventRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.TransactionPort;
import co.edu.univalle.fleetops.vehicles.application.result.IncomingEventResult;
import co.edu.univalle.fleetops.vehicles.application.support.PassThroughTransactionPort;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessIncomingEventServiceTest {

    private static final String TYPE = "severe_incident_reported_event";

    @Mock
    private ProcessedEventRepository processed;
    @Mock
    private IncomingEventHandler handler;

    private ProcessIncomingEventService service;

    private final IncomingEvent event = new IncomingEvent("evt-1", TYPE, "corr-1", Map.of("vehicle_id", "v1"));

    @BeforeEach
    void setUp() {
        lenient().when(handler.eventType()).thenReturn(TYPE);
        service = new ProcessIncomingEventService(List.of(handler), processed, new PassThroughTransactionPort(),
                CLOCK);
    }

    @Test
    void execute_newEvent_registersItAndRunsTheHandler() {
        when(processed.markProcessed("evt-1", TYPE, NOW)).thenReturn(true);
        when(handler.handle(event)).thenReturn(true);

        IncomingEventResult result = service.execute(event);

        assertThat(result).isEqualTo(IncomingEventResult.PROCESSED);
        InOrder order = inOrder(processed, handler);
        order.verify(processed).markProcessed("evt-1", TYPE, NOW);
        order.verify(handler).handle(event);
    }

    @Test
    void execute_handlerWithoutEffect_registersItAndReturnsIgnored() {
        when(processed.markProcessed("evt-1", TYPE, NOW)).thenReturn(true);
        when(handler.handle(event)).thenReturn(false);

        IncomingEventResult result = service.execute(event);

        assertThat(result).isEqualTo(IncomingEventResult.IGNORED);
        verify(processed).markProcessed("evt-1", TYPE, NOW);
    }

    @Test
    void execute_alreadyProcessedEvent_changesNothing() {
        when(processed.markProcessed("evt-1", TYPE, NOW)).thenReturn(false);

        IncomingEventResult result = service.execute(event);

        assertThat(result).isEqualTo(IncomingEventResult.DUPLICATE);
        verify(handler, never()).handle(event);
    }

    @Test
    void execute_unknownEventType_isIgnoredWithoutTouchingTheDatabase() {
        TransactionPort transaction = mock(TransactionPort.class);
        service = new ProcessIncomingEventService(List.of(handler), processed, transaction, CLOCK);
        IncomingEvent unknown = new IncomingEvent("evt-2", "something_else_event", null, Map.of());

        IncomingEventResult result = service.execute(unknown);

        assertThat(result).isEqualTo(IncomingEventResult.UNKNOWN_TYPE);
        verifyNoInteractions(transaction, processed);
        verify(handler, never()).handle(any());
    }

    @Test
    void execute_noHandlersAtAll_everyEventIsUnknown() {
        service = new ProcessIncomingEventService(List.of(), processed, new PassThroughTransactionPort(), CLOCK);

        assertThat(service.execute(event)).isEqualTo(IncomingEventResult.UNKNOWN_TYPE);
    }

    @Test
    void execute_handlerFails_propagatesTheErrorSoNothingStaysAndTheMessageIsRetried() {
        when(processed.markProcessed("evt-1", TYPE, NOW)).thenReturn(true);
        doThrow(new IllegalStateException("boom")).when(handler).handle(event);

        assertThatThrownBy(() -> service.execute(event)).isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    @Test
    void execute_runsEverythingInsideOneTransaction() {
        TransactionPort transaction = mock(TransactionPort.class);
        when(transaction.<IncomingEventResult>execute(any()))
                .thenReturn(IncomingEventResult.PROCESSED);
        service = new ProcessIncomingEventService(List.of(handler), processed, transaction, CLOCK);

        IncomingEventResult result = service.execute(event);

        assertThat(result).isEqualTo(IncomingEventResult.PROCESSED);
        verifyNoInteractions(processed);
        verify(handler, never()).handle(any());
    }

    @Test
    void constructor_twoHandlersForTheSameType_isRejected() {
        IncomingEventHandler other = mock(IncomingEventHandler.class);
        when(other.eventType()).thenReturn(TYPE);
        var handlers = List.of(handler, other);
        var transaction = new PassThroughTransactionPort();

        assertThatThrownBy(() -> new ProcessIncomingEventService(handlers, processed, transaction, CLOCK))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(TYPE);
    }
}
