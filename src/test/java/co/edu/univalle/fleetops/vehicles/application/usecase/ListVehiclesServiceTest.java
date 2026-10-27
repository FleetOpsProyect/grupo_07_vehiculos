package co.edu.univalle.fleetops.vehicles.application.usecase;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.activeVehicle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.query.ListVehiclesQuery;
import co.edu.univalle.fleetops.vehicles.application.result.PageResult;
import co.edu.univalle.fleetops.vehicles.application.result.VehiclePageResult;
import co.edu.univalle.fleetops.vehicles.domain.exception.FieldError;
import co.edu.univalle.fleetops.vehicles.domain.exception.ValidationException;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListVehiclesServiceTest {

    @Mock
    private VehicleRepository vehicles;
    @InjectMocks
    private ListVehiclesService service;

    @Test
    void execute_returnsPageWithSummaryFilledWithZeros() {
        Vehicle vehicle = activeVehicle();
        when(vehicles.search(VehicleStatus.ACTIVE, VehicleType.PICKUP_TRUCK, 0, 20))
                .thenReturn(new PageResult<>(List.of(vehicle), 0, 20, 1));
        when(vehicles.countByStatus()).thenReturn(Map.of(VehicleStatus.ACTIVE, 37L, VehicleStatus.BLOCKED, 2L));

        VehiclePageResult result = service.execute(
                new ListVehiclesQuery(VehicleStatus.ACTIVE, VehicleType.PICKUP_TRUCK, 0, 20));

        assertThat(result.content()).singleElement().satisfies(row -> assertThat(row.plate()).isEqualTo("ABC123"));
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.summary()).containsExactly(
                Map.entry(VehicleStatus.ACTIVE, 37L), Map.entry(VehicleStatus.ON_ROUTE, 0L),
                Map.entry(VehicleStatus.BLOCKED, 2L), Map.entry(VehicleStatus.IN_MAINTENANCE, 0L),
                Map.entry(VehicleStatus.INACTIVE_EXPIRED_DOCUMENTS, 0L));
    }

    @Test
    void execute_sizeOver100AndNegativePage_reportsBothFields() {
        var query = new ListVehiclesQuery(null, null, -1, 101);

        assertThatThrownBy(() -> service.execute(query))
                .isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.errors())
                        .containsExactly(new FieldError("page", "OUT_OF_RANGE"),
                                new FieldError("size", "OUT_OF_RANGE")));
        verifyNoInteractions(vehicles);
    }

    @Test
    void execute_sizeZero_isOutOfRange() {
        var query = new ListVehiclesQuery(null, null, 0, 0);

        assertThatThrownBy(() -> service.execute(query))
                .isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.errors())
                        .containsExactly(new FieldError("size", "OUT_OF_RANGE")));
    }
}
