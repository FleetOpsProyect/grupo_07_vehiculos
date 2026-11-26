package co.edu.univalle.fleetops.vehicles.application.usecase;

import co.edu.univalle.fleetops.vehicles.application.command.UpdateVehicleCommand;
import co.edu.univalle.fleetops.vehicles.application.event.VehicleEvents;
import co.edu.univalle.fleetops.vehicles.application.port.in.UpdateVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.out.EventOutbox;
import co.edu.univalle.fleetops.vehicles.application.port.out.TransactionPort;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleResult;
import co.edu.univalle.fleetops.vehicles.domain.exception.VehicleNotFoundException;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleDetails;
import co.edu.univalle.fleetops.vehicles.domain.rule.VehicleValidationRules;
import co.edu.univalle.fleetops.vehicles.domain.exception.FieldError;
import co.edu.univalle.fleetops.vehicles.domain.exception.ValidationException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/**
 * Edición de marca, modelo, año, color y observaciones; un vehículo eliminado responde 404. Si algún
 * campo cambia, sube la versión y registra {@code vehicle_updated_event}; si ninguno cambia, no guarda
 * nada ni publica.
 */
public class UpdateVehicleService implements UpdateVehicleUseCase {

    private final VehicleRepository vehicles;
    private final EventOutbox outbox;
    private final TransactionPort transaction;
    private final Clock clock;

    /**
     * Crea el caso de uso con sus puertos.
     *
     * @param vehicles persistencia de vehículos
     * @param outbox registro de eventos en la misma transacción
     * @param transaction ejecución transaccional
     * @param clock reloj en GMT-5
     */
    public UpdateVehicleService(VehicleRepository vehicles, EventOutbox outbox, TransactionPort transaction,
                                Clock clock) {
        this.vehicles = vehicles;
        this.outbox = outbox;
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
            List<FieldError> errors = new ArrayList<>(command.preValidationErrors());
            try {
                VehicleValidationRules.validateDetails(details, Year.now(clock).getValue());
            } catch (ValidationException e) {
                errors.addAll(e.errors());
            }
            if (!errors.isEmpty()) {
                throw new ValidationException(errors);
            }
            List<String> changed = vehicle.update(details, OffsetDateTime.now(clock));
            if (changed.isEmpty()) {
                return VehicleResult.from(vehicle);
            }
            Vehicle saved = vehicles.save(vehicle);
            outbox.record(VehicleEvents.UPDATED, saved.id(), VehicleEvents.updated(saved, changed));
            return VehicleResult.from(saved);
        });
    }
}
