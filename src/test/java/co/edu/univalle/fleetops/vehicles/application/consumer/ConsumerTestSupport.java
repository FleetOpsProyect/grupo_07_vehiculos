package co.edu.univalle.fleetops.vehicles.application.consumer;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.CLOCK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

import co.edu.univalle.fleetops.vehicles.application.event.IncomingEvent;
import co.edu.univalle.fleetops.vehicles.application.port.out.EventOutbox;
import co.edu.univalle.fleetops.vehicles.application.port.out.StatusHistoryRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusHistoryEntry;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Mocks y ayudas comunes de las pruebas de los consumidores. */
@ExtendWith(MockitoExtension.class)
abstract class ConsumerTestSupport {

    @Mock
    protected VehicleRepository vehicles;
    @Mock
    protected StatusHistoryRepository history;
    @Mock
    protected EventOutbox outbox;
    @Captor
    protected ArgumentCaptor<Map<String, Object>> data;
    @Captor
    protected ArgumentCaptor<StatusHistoryEntry> entry;

    protected final Clock clock = CLOCK;

    @BeforeEach
    void saveReturnsTheSameVehicle() {
        lenient().when(vehicles.save(any(Vehicle.class))).then(returnsFirstArg());
    }

    protected static IncomingEvent event(String type, Object... keyValues) {
        Map<String, Object> data = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            data.put((String) keyValues[i], keyValues[i + 1]);
        }
        return new IncomingEvent("evt-1", type, null, data);
    }

    protected void stored(Vehicle vehicle) {
        when(vehicles.findById(vehicle.id())).thenReturn(Optional.of(vehicle));
    }

    protected void unknown(UUID vehicleId) {
        when(vehicles.findById(vehicleId)).thenReturn(Optional.empty());
    }

    protected Map<String, Object> recordedData(String eventType, Vehicle vehicle) {
        verify(outbox).record(eq(eventType), eq(vehicle.id()), data.capture());
        return data.getValue();
    }

    protected StatusHistoryEntry historyEntry() {
        verify(history).save(entry.capture());
        return entry.getValue();
    }

    protected void nothingChanged() {
        verify(vehicles, never()).save(any());
        verifyNoInteractions(history, outbox);
    }
}
