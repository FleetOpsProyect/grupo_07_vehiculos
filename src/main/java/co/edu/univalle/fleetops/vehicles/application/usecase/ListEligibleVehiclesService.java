package co.edu.univalle.fleetops.vehicles.application.usecase;

import co.edu.univalle.fleetops.vehicles.application.port.in.ListEligibleVehiclesUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.out.DocumentValidityPort;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.query.ListEligibleVehiclesQuery;
import co.edu.univalle.fleetops.vehicles.application.result.EligibleVehiclesResult;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleSummaryResult;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Vehículos ACTIVE con documentos vigentes hasta una fecha. Consulta a Documentación por lotes de
 * 100 y entrega hasta 500; si Documentación falla, la excepción sube y no se devuelve nada.
 */
public class ListEligibleVehiclesService implements ListEligibleVehiclesUseCase {

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RESULTS = 500;

    private final VehicleRepository vehicles;
    private final DocumentValidityPort documents;

    /**
     * Crea el caso de uso con sus puertos.
     *
     * @param vehicles persistencia de vehículos
     * @param documents consulta de vigencia a Documentación
     */
    public ListEligibleVehiclesService(VehicleRepository vehicles, DocumentValidityPort documents) {
        this.vehicles = vehicles;
        this.documents = documents;
    }

    @Override
    public EligibleVehiclesResult execute(ListEligibleVehiclesQuery query) {
        List<Vehicle> candidates = vehicles.findActiveByType(query.type());
        Set<UUID> valid = new HashSet<>();
        for (int from = 0; from < candidates.size(); from += BATCH_SIZE) {
            List<UUID> batch = candidates.subList(from, Math.min(from + BATCH_SIZE, candidates.size()))
                    .stream().map(Vehicle::id).toList();
            valid.addAll(documents.findVehiclesWithValidDocuments(batch, query.validUntil()));
        }
        List<Vehicle> eligible = candidates.stream().filter(vehicle -> valid.contains(vehicle.id())).toList();
        List<VehicleSummaryResult> content = eligible.stream().limit(MAX_RESULTS)
                .map(VehicleSummaryResult::from).toList();
        return new EligibleVehiclesResult(content, eligible.size(), eligible.size() > MAX_RESULTS);
    }
}
