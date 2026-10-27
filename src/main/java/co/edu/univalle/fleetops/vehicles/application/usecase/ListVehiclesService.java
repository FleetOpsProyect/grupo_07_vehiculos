package co.edu.univalle.fleetops.vehicles.application.usecase;

import co.edu.univalle.fleetops.vehicles.application.port.in.ListVehiclesUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.query.ListVehiclesQuery;
import co.edu.univalle.fleetops.vehicles.application.result.PageResult;
import co.edu.univalle.fleetops.vehicles.application.result.VehiclePageResult;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleSummaryResult;
import co.edu.univalle.fleetops.vehicles.domain.exception.FieldError;
import co.edu.univalle.fleetops.vehicles.domain.exception.ValidationException;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Listado paginado con el conteo por estado de toda la flota para Reportes. */
public class ListVehiclesService implements ListVehiclesUseCase {

    private static final int MAX_PAGE_SIZE = 100;
    private static final List<VehicleStatus> SUMMARY_STATUSES = List.of(VehicleStatus.ACTIVE,
            VehicleStatus.ON_ROUTE, VehicleStatus.BLOCKED, VehicleStatus.IN_MAINTENANCE,
            VehicleStatus.INACTIVE_EXPIRED_DOCUMENTS);

    private final VehicleRepository vehicles;

    /**
     * Crea el caso de uso con sus puertos.
     *
     * @param vehicles persistencia de vehículos
     */
    public ListVehiclesService(VehicleRepository vehicles) {
        this.vehicles = vehicles;
    }

    @Override
    public VehiclePageResult execute(ListVehiclesQuery query) {
        validatePaging(query);
        PageResult<VehicleSummaryResult> page = vehicles
                .search(query.status(), query.type(), query.page(), query.size())
                .map(VehicleSummaryResult::from);
        return new VehiclePageResult(page.content(), page.page(), page.size(), page.total(), buildSummary());
    }

    private void validatePaging(ListVehiclesQuery query) {
        List<FieldError> errors = new ArrayList<>();
        if (query.page() < 0) {
            errors.add(new FieldError("page", "OUT_OF_RANGE"));
        }
        if (query.size() < 1 || query.size() > MAX_PAGE_SIZE) {
            errors.add(new FieldError("size", "OUT_OF_RANGE"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private Map<VehicleStatus, Long> buildSummary() {
        Map<VehicleStatus, Long> counts = vehicles.countByStatus();
        Map<VehicleStatus, Long> summary = new LinkedHashMap<>();
        SUMMARY_STATUSES.forEach(status -> summary.put(status, counts.getOrDefault(status, 0L)));
        return summary;
    }
}
