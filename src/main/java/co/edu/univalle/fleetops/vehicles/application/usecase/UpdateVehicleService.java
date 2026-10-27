package co.edu.univalle.fleetops.vehicles.application.usecase;

import co.edu.univalle.fleetops.vehicles.application.command.UpdateVehicleCommand;
import co.edu.univalle.fleetops.vehicles.application.port.in.UpdateVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.out.TransactionPort;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleResult;
import co.edu.univalle.fleetops.vehicles.domain.exception.VehicleNotFoundException;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleDetails;
import co.edu.univalle.fleetops.vehicles.domain.rule.VehicleValidationRules;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.Year;

/** Edición de marca, modelo, año, color y observaciones; un vehículo eliminado responde 404. */
public class UpdateVehicleService implements UpdateVehicleUseCase {

    private final VehicleRepository vehicles;
    private final TransactionPort transaction;
    private final Clock clock;

    /**
     * Crea el caso de uso con sus puertos.
     *
     * @param vehicles persistencia de vehículos
     * @param transaction ejecución transaccional
     * @param clock reloj en GMT-5
     */
    public UpdateVehicleService(VehicleRepository vehicles, TransactionPort transaction, Clock clock) {
        this.vehicles = vehicles;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public VehicleResult execute(UpdateVehicleCommand command) {
        return transaction.execute(() -> {
            Vehicle vehicle = vehicles.findById(command.vehicleId())
                    .filter(found -> !found.isDeleted())
                    .orElseThrow(() -> new VehicleNotFoundException(command.vehicleId()));
            VehicleDetails details = new VehicleDetails(command.brand(), command.model(), command.year(),
                    command.color(), command.notes());
            VehicleValidationRules.validateDetails(details, Year.now(clock).getValue());
            vehicle.update(details, OffsetDateTime.now(clock));
            return VehicleResult.from(vehicles.save(vehicle));
        });
    }
}
