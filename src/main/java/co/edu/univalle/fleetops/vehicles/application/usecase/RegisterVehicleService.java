package co.edu.univalle.fleetops.vehicles.application.usecase;

import co.edu.univalle.fleetops.vehicles.application.command.RegisterVehicleCommand;
import co.edu.univalle.fleetops.vehicles.application.port.in.RegisterVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.out.StatusHistoryRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.TransactionPort;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleResult;
import co.edu.univalle.fleetops.vehicles.domain.exception.PlateAlreadyExistsException;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusChangeCause;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusHistoryEntry;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleDetails;
import co.edu.univalle.fleetops.vehicles.domain.rule.VehicleValidationRules;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.Year;

/** Alta de un vehículo en ACTIVE, con su primer registro en el historial. */
public class RegisterVehicleService implements RegisterVehicleUseCase {

    private final VehicleRepository vehicles;
    private final StatusHistoryRepository history;
    private final TransactionPort transaction;
    private final Clock clock;

    /**
     * Crea el caso de uso con sus puertos.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial de estados
     * @param transaction ejecución transaccional
     * @param clock reloj en GMT-5
     */
    public RegisterVehicleService(VehicleRepository vehicles, StatusHistoryRepository history,
                                  TransactionPort transaction, Clock clock) {
        this.vehicles = vehicles;
        this.history = history;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public VehicleResult execute(RegisterVehicleCommand command) {
        String plate = VehicleValidationRules.normalizePlate(command.plate());
        VehicleDetails details = new VehicleDetails(command.brand(), command.model(), command.year(),
                command.color(), command.notes());
        VehicleValidationRules.validateNewVehicle(plate, command.vehicleType(), details, command.odometerKm(),
                Year.now(clock).getValue());
        return transaction.execute(() -> {
            if (vehicles.existsActiveByPlate(plate)) {
                throw new PlateAlreadyExistsException(plate);
            }
            OffsetDateTime now = OffsetDateTime.now(clock);
            Vehicle saved = vehicles.save(
                    Vehicle.register(plate, command.vehicleType(), details, command.odometerKm(), now));
            history.save(StatusHistoryEntry.of(saved.id(), null, saved.status(),
                    StatusChangeCause.VEHICLE_CREATED, null, now));
            return VehicleResult.from(saved);
        });
    }
}
