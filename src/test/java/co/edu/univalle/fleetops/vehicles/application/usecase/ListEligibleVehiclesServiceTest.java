package co.edu.univalle.fleetops.vehicles.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.univalle.fleetops.vehicles.application.port.out.DocumentValidityPort;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.query.ListEligibleVehiclesQuery;
import co.edu.univalle.fleetops.vehicles.application.result.EligibleVehiclesResult;
import co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures;
import co.edu.univalle.fleetops.vehicles.application.exception.DocumentServiceUnavailableException;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListEligibleVehiclesServiceTest {

    private static final LocalDate VALID_UNTIL = LocalDate.of(2026, 11, 15);
    private static final ListEligibleVehiclesQuery QUERY =
            new ListEligibleVehiclesQuery(VehicleType.PICKUP_TRUCK, VALID_UNTIL);

    @Mock
    private VehicleRepository vehicles;
    @Mock
    private DocumentValidityPort documents;
    @InjectMocks
    private ListEligibleVehiclesService service;

    private static List<Vehicle> candidates(int count) {
        return IntStream.range(0, count).mapToObj(i -> VehicleFixtures.activeVehicle()).toList();
    }

    @Test
    void execute_returnsOnlyVehiclesDocumentationConfirms() {
        List<Vehicle> all = candidates(3);
        when(vehicles.findActiveByType(VehicleType.PICKUP_TRUCK)).thenReturn(all);
        when(documents.findVehiclesWithValidDocuments(anyList(), eq(VALID_UNTIL)))
                .thenReturn(new HashSet<>(List.of(all.get(0).id(), all.get(2).id())));

        EligibleVehiclesResult result = service.execute(QUERY);

        assertThat(result.content()).extracting(row -> row.vehicleId())
                .containsExactly(all.get(0).id(), all.get(2).id());
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void execute_noCandidates_neverCallsDocumentation() {
        when(vehicles.findActiveByType(VehicleType.PICKUP_TRUCK)).thenReturn(List.of());

        EligibleVehiclesResult result = service.execute(QUERY);

        assertThat(result.content()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.truncated()).isFalse();
        verifyNoInteractions(documents);
    }

    @Test
    void execute_250Candidates_callsDocumentationInBatchesOf100() {
        when(vehicles.findActiveByType(VehicleType.PICKUP_TRUCK)).thenReturn(candidates(250));
        when(documents.findVehiclesWithValidDocuments(anyList(), eq(VALID_UNTIL))).thenReturn(new HashSet<>());

        service.execute(QUERY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<java.util.UUID>> batches = ArgumentCaptor.forClass(List.class);
        verify(documents, times(3)).findVehiclesWithValidDocuments(batches.capture(), eq(VALID_UNTIL));
        assertThat(batches.getAllValues()).extracting(List::size).containsExactly(100, 100, 50);
    }

    @Test
    void execute_moreThan500Eligible_truncatesButReportsTotal() {
        when(vehicles.findActiveByType(VehicleType.PICKUP_TRUCK)).thenReturn(candidates(501));
        when(documents.findVehiclesWithValidDocuments(anyList(), eq(VALID_UNTIL)))
                .thenAnswer(call -> new HashSet<>(call.<List<java.util.UUID>>getArgument(0)));

        EligibleVehiclesResult result = service.execute(QUERY);

        assertThat(result.content()).hasSize(500);
        assertThat(result.total()).isEqualTo(501);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void execute_exactly500Eligible_isNotTruncated() {
        when(vehicles.findActiveByType(VehicleType.PICKUP_TRUCK)).thenReturn(candidates(500));
        when(documents.findVehiclesWithValidDocuments(anyList(), eq(VALID_UNTIL)))
                .thenAnswer(call -> new HashSet<>(call.<List<java.util.UUID>>getArgument(0)));

        assertThat(service.execute(QUERY).truncated()).isFalse();
    }

    @Test
    void execute_documentationDown_propagatesAndReturnsNothing() {
        when(vehicles.findActiveByType(VehicleType.PICKUP_TRUCK)).thenReturn(candidates(1));
        when(documents.findVehiclesWithValidDocuments(anyList(), eq(VALID_UNTIL)))
                .thenThrow(new DocumentServiceUnavailableException(new RuntimeException("timeout")));

        assertThatThrownBy(() -> service.execute(QUERY)).isInstanceOf(DocumentServiceUnavailableException.class);
    }
}
