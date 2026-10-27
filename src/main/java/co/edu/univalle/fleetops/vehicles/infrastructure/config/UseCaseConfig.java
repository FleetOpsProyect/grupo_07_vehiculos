package co.edu.univalle.fleetops.vehicles.infrastructure.config;

import co.edu.univalle.fleetops.vehicles.application.port.in.ChangeToMaintenanceUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.DeleteVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.GetVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.ListEligibleVehiclesUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.ListVehiclesUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.RegisterVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.UpdateVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.out.ActiveAssignmentsPort;
import co.edu.univalle.fleetops.vehicles.application.port.out.DocumentValidityPort;
import co.edu.univalle.fleetops.vehicles.application.port.out.StatusHistoryRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.TransactionPort;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.usecase.ChangeToMaintenanceService;
import co.edu.univalle.fleetops.vehicles.application.usecase.DeleteVehicleService;
import co.edu.univalle.fleetops.vehicles.application.usecase.GetVehicleService;
import co.edu.univalle.fleetops.vehicles.application.usecase.ListEligibleVehiclesService;
import co.edu.univalle.fleetops.vehicles.application.usecase.ListVehiclesService;
import co.edu.univalle.fleetops.vehicles.application.usecase.RegisterVehicleService;
import co.edu.univalle.fleetops.vehicles.application.usecase.UpdateVehicleService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root: único lugar donde se conectan los casos de uso con sus puertos de salida.
 * Los casos de uso no llevan anotaciones de Spring.
 */
@Configuration
public class UseCaseConfig {

    /**
     * Caso de uso de alta.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial
     * @param transaction ejecución transaccional
     * @param clock reloj en GMT-5
     * @return el caso de uso
     */
    @Bean
    public RegisterVehicleUseCase registerVehicleUseCase(VehicleRepository vehicles,
            StatusHistoryRepository history, TransactionPort transaction, Clock clock) {
        return new RegisterVehicleService(vehicles, history, transaction, clock);
    }

    /**
     * Caso de uso de detalle.
     *
     * @param vehicles persistencia de vehículos
     * @return el caso de uso
     */
    @Bean
    public GetVehicleUseCase getVehicleUseCase(VehicleRepository vehicles) {
        return new GetVehicleService(vehicles);
    }

    /**
     * Caso de uso de listado paginado.
     *
     * @param vehicles persistencia de vehículos
     * @return el caso de uso
     */
    @Bean
    public ListVehiclesUseCase listVehiclesUseCase(VehicleRepository vehicles) {
        return new ListVehiclesService(vehicles);
    }

    /**
     * Caso de uso de vehículos aptos.
     *
     * @param vehicles persistencia de vehículos
     * @param documents consulta de vigencia a Documentación
     * @return el caso de uso
     */
    @Bean
    public ListEligibleVehiclesUseCase listEligibleVehiclesUseCase(VehicleRepository vehicles,
            DocumentValidityPort documents) {
        return new ListEligibleVehiclesService(vehicles, documents);
    }

    /**
     * Caso de uso de edición.
     *
     * @param vehicles persistencia de vehículos
     * @param transaction ejecución transaccional
     * @param clock reloj en GMT-5
     * @return el caso de uso
     */
    @Bean
    public UpdateVehicleUseCase updateVehicleUseCase(VehicleRepository vehicles, TransactionPort transaction,
            Clock clock) {
        return new UpdateVehicleService(vehicles, transaction, clock);
    }

    /**
     * Caso de uso de paso a mantenimiento.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial
     * @param transaction ejecución transaccional
     * @param clock reloj en GMT-5
     * @return el caso de uso
     */
    @Bean
    public ChangeToMaintenanceUseCase changeToMaintenanceUseCase(VehicleRepository vehicles,
            StatusHistoryRepository history, TransactionPort transaction, Clock clock) {
        return new ChangeToMaintenanceService(vehicles, history, transaction, clock);
    }

    /**
     * Caso de uso de baja lógica.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial
     * @param assignments consulta de asignaciones activas a Asignaciones
     * @param transaction ejecución transaccional
     * @param clock reloj en GMT-5
     * @return el caso de uso
     */
    @Bean
    public DeleteVehicleUseCase deleteVehicleUseCase(VehicleRepository vehicles, StatusHistoryRepository history,
            ActiveAssignmentsPort assignments, TransactionPort transaction, Clock clock) {
        return new DeleteVehicleService(vehicles, history, assignments, transaction, clock);
    }
}
