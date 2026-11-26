package co.edu.univalle.fleetops.vehicles.infrastructure.config;

import co.edu.univalle.fleetops.vehicles.application.port.in.ChangeToMaintenanceUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.DeleteVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.GetVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.ListEligibleVehiclesUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.IncomingEventHandler;
import co.edu.univalle.fleetops.vehicles.application.port.in.ListVehiclesUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.ProcessIncomingEventUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.RegisterVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.UpdateVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.out.ActiveAssignmentsPort;
import co.edu.univalle.fleetops.vehicles.application.port.out.AppliedRouteRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.DocumentValidityPort;
import co.edu.univalle.fleetops.vehicles.application.port.out.EventOutbox;
import co.edu.univalle.fleetops.vehicles.application.port.out.ProcessedEventRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.StatusHistoryRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.TransactionPort;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.usecase.ChangeToMaintenanceService;
import co.edu.univalle.fleetops.vehicles.application.usecase.DeleteVehicleService;
import co.edu.univalle.fleetops.vehicles.application.consumer.DocumentExpiredHandler;
import co.edu.univalle.fleetops.vehicles.application.consumer.DocumentRenewedHandler;
import co.edu.univalle.fleetops.vehicles.application.usecase.GetVehicleService;
import co.edu.univalle.fleetops.vehicles.application.usecase.ListEligibleVehiclesService;
import co.edu.univalle.fleetops.vehicles.application.usecase.ListVehiclesService;
import co.edu.univalle.fleetops.vehicles.application.consumer.MaintenanceCompletedHandler;
import co.edu.univalle.fleetops.vehicles.application.usecase.ProcessIncomingEventService;
import co.edu.univalle.fleetops.vehicles.application.usecase.RegisterVehicleService;
import co.edu.univalle.fleetops.vehicles.application.consumer.RouteFinishedHandler;
import co.edu.univalle.fleetops.vehicles.application.consumer.RouteStartedHandler;
import co.edu.univalle.fleetops.vehicles.application.consumer.SevereIncidentReportedHandler;
import co.edu.univalle.fleetops.vehicles.application.usecase.UpdateVehicleService;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
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
     * @param outbox registro de eventos en la misma transacción
     * @param transaction ejecución transaccional
     * @param clock reloj en GMT-5
     * @return el caso de uso
     */
    @Bean
    public RegisterVehicleUseCase registerVehicleUseCase(VehicleRepository vehicles,
            StatusHistoryRepository history, EventOutbox outbox, TransactionPort transaction, Clock clock) {
        return new RegisterVehicleService(vehicles, history, outbox, transaction, clock);
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
     * @param outbox registro de eventos en la misma transacción
     * @param transaction ejecución transaccional
     * @param clock reloj en GMT-5
     * @return el caso de uso
     */
    @Bean
    public UpdateVehicleUseCase updateVehicleUseCase(VehicleRepository vehicles, EventOutbox outbox,
            TransactionPort transaction, Clock clock) {
        return new UpdateVehicleService(vehicles, outbox, transaction, clock);
    }

    /**
     * Caso de uso de paso a mantenimiento.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial
     * @param outbox registro de eventos en la misma transacción
     * @param transaction ejecución transaccional
     * @param clock reloj en GMT-5
     * @return el caso de uso
     */
    @Bean
    public ChangeToMaintenanceUseCase changeToMaintenanceUseCase(VehicleRepository vehicles,
            StatusHistoryRepository history, EventOutbox outbox, TransactionPort transaction, Clock clock) {
        return new ChangeToMaintenanceService(vehicles, history, outbox, transaction, clock);
    }

    /**
     * Caso de uso de baja lógica.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial
     * @param assignments consulta de asignaciones activas a Asignaciones
     * @param outbox registro de eventos en la misma transacción
     * @param transaction ejecución transaccional
     * @param clock reloj en GMT-5
     * @return el caso de uso
     */
    @Bean
    public DeleteVehicleUseCase deleteVehicleUseCase(VehicleRepository vehicles, StatusHistoryRepository history,
            ActiveAssignmentsPort assignments, EventOutbox outbox, TransactionPort transaction, Clock clock) {
        return new DeleteVehicleService(vehicles, history, assignments, outbox, transaction, clock);
    }

    /**
     * Caso de uso que aplica un mensaje recibido una sola vez, con los manejadores registrados.
     *
     * @param handlers manejadores de cada {@code event_type}; puede no haber ninguno
     * @param processed registro de mensajes ya procesados
     * @param transaction ejecución transaccional
     * @param clock reloj en GMT-5
     * @return el caso de uso
     */
    @Bean
    public ProcessIncomingEventUseCase processIncomingEventUseCase(ObjectProvider<IncomingEventHandler> handlers,
            ProcessedEventRepository processed, TransactionPort transaction, Clock clock) {
        return new ProcessIncomingEventService(handlers.orderedStream().toList(), processed, transaction, clock);
    }

    /**
     * Consumidor de {@code severe_incident_reported_event}.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial
     * @param outbox registro de eventos en la misma transacción
     * @param clock reloj en GMT-5
     * @return el consumidor
     */
    @Bean
    public IncomingEventHandler severeIncidentReportedHandler(VehicleRepository vehicles,
            StatusHistoryRepository history, EventOutbox outbox, Clock clock) {
        return new SevereIncidentReportedHandler(vehicles, history, outbox, clock);
    }

    /**
     * Consumidor de {@code maintenance_completed_event}.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial
     * @param outbox registro de eventos en la misma transacción
     * @param clock reloj en GMT-5
     * @return el consumidor
     */
    @Bean
    public IncomingEventHandler maintenanceCompletedHandler(VehicleRepository vehicles, StatusHistoryRepository history,
            EventOutbox outbox, Clock clock) {
        return new MaintenanceCompletedHandler(vehicles, history, outbox, clock);
    }

    /**
     * Consumidor de {@code document_expired_event}.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial
     * @param outbox registro de eventos en la misma transacción
     * @param clock reloj en GMT-5
     * @return el consumidor
     */
    @Bean
    public IncomingEventHandler documentExpiredHandler(VehicleRepository vehicles, StatusHistoryRepository history,
            EventOutbox outbox, Clock clock) {
        return new DocumentExpiredHandler(vehicles, history, outbox, clock);
    }

    /**
     * Consumidor de {@code document_renewed_event}.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial
     * @param outbox registro de eventos en la misma transacción
     * @param clock reloj en GMT-5
     * @return el consumidor
     */
    @Bean
    public IncomingEventHandler documentRenewedHandler(VehicleRepository vehicles, StatusHistoryRepository history,
            EventOutbox outbox, Clock clock) {
        return new DocumentRenewedHandler(vehicles, history, outbox, clock);
    }

    /**
     * Consumidor de {@code route_started_event}.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial
     * @param outbox registro de eventos en la misma transacción
     * @param clock reloj en GMT-5
     * @param routes rutas ya sumadas
     * @return el consumidor
     */
    @Bean
    public IncomingEventHandler routeStartedHandler(VehicleRepository vehicles, StatusHistoryRepository history,
            EventOutbox outbox, Clock clock, AppliedRouteRepository routes) {
        return new RouteStartedHandler(vehicles, history, outbox, clock, routes);
    }

    /**
     * Consumidor de {@code route_finished_event}.
     *
     * @param vehicles persistencia de vehículos
     * @param history persistencia del historial
     * @param outbox registro de eventos en la misma transacción
     * @param clock reloj en GMT-5
     * @param routes rutas ya sumadas
     * @return el consumidor
     */
    @Bean
    public IncomingEventHandler routeFinishedHandler(VehicleRepository vehicles, StatusHistoryRepository history,
            EventOutbox outbox, Clock clock, AppliedRouteRepository routes) {
        return new RouteFinishedHandler(vehicles, history, outbox, clock, routes);
    }
}
