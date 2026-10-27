package co.edu.univalle.fleetops.vehicles.application.port.out;

import co.edu.univalle.fleetops.vehicles.application.result.PageResult;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistencia de vehículos. */
public interface VehicleRepository {

    /**
     * Guarda un vehículo nuevo o los cambios de uno existente.
     *
     * @param vehicle vehículo a guardar
     * @return el vehículo guardado
     */
    Vehicle save(Vehicle vehicle);

    /**
     * Busca por id, incluidos los eliminados.
     *
     * @param id identificador
     * @return el vehículo, si existe
     */
    Optional<Vehicle> findById(UUID id);

    /**
     * Indica si hay un vehículo no eliminado con esa placa.
     *
     * @param plate placa normalizada
     * @return {@code true} si la placa está en uso
     */
    boolean existsActiveByPlate(String plate);

    /**
     * Lista una página filtrada. Sin filtro de estado no incluye los eliminados.
     *
     * @param status estado, o nulo para todos menos DELETED
     * @param type tipo, o nulo para todos
     * @param page página desde 0
     * @param size tamaño de página
     * @return la página pedida
     */
    PageResult<Vehicle> search(VehicleStatus status, VehicleType type, int page, int size);

    /**
     * Cuenta los vehículos no eliminados por estado.
     *
     * @return conteos; los estados sin vehículos pueden faltar
     */
    Map<VehicleStatus, Long> countByStatus();

    /**
     * Lista los vehículos ACTIVE, candidatos a aptos.
     *
     * @param type tipo, o nulo para todos
     * @return los candidatos en un orden estable
     */
    List<Vehicle> findActiveByType(VehicleType type);
}
