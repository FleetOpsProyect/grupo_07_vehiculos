package co.edu.univalle.fleetops.vehicles.repository;

import co.edu.univalle.fleetops.vehicles.model.VehicleModel;
import co.edu.univalle.fleetops.vehicles.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.model.VehicleType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA de vehículos.
 * Spring Data genera las consultas a partir del nombre de cada método.
 */
@Repository
public interface VehicleRepository extends JpaRepository<VehicleModel, UUID> {

    /**
     * Indica si ya existe un vehículo no eliminado con la placa dada.
     * @param plate placa normalizada
     * @return true si existe
     */
    boolean existsByPlateAndDeletedFalse(String plate);

    /**
     * Lista los vehículos no eliminados, paginados.
     * @param pageable página solicitada
     * @return página de vehículos
     */
    Page<VehicleModel> findAllByDeletedFalse(Pageable pageable);

    /**
     * Lista los vehículos no eliminados con el estado dado, paginados.
     * @param status estado
     * @param pageable página solicitada
     * @return página de vehículos
     */
    Page<VehicleModel> findAllByStatusAndDeletedFalse(VehicleStatus status, Pageable pageable);

    /**
     * Lista los vehículos no eliminados del tipo dado, paginados.
     * @param vehicleType tipo de vehículo
     * @param pageable página solicitada
     * @return página de vehículos
     */
    Page<VehicleModel> findAllByVehicleTypeAndDeletedFalse(VehicleType vehicleType, Pageable pageable);

    /**
     * Lista los vehículos no eliminados con el estado y el tipo dados, paginados.
     * @param status estado
     * @param vehicleType tipo de vehículo
     * @param pageable página solicitada
     * @return página de vehículos
     */
    Page<VehicleModel> findAllByStatusAndVehicleTypeAndDeletedFalse(
            VehicleStatus status, VehicleType vehicleType, Pageable pageable);

    /**
     * Candidatos para el listado de aptos: todos los vehículos con el estado dado.
     * @param status estado, normalmente ACTIVE
     * @return vehículos ordenados por placa
     */
    List<VehicleModel> findAllByStatusAndDeletedFalseOrderByPlateAsc(VehicleStatus status);

    /**
     * Candidatos para el listado de aptos filtrados por tipo.
     * @param status estado, normalmente ACTIVE
     * @param vehicleType tipo de vehículo
     * @return vehículos ordenados por placa
     */
    List<VehicleModel> findAllByStatusAndVehicleTypeAndDeletedFalseOrderByPlateAsc(
            VehicleStatus status, VehicleType vehicleType);

    /**
     * Cuenta los vehículos no eliminados por estado, para el bloque resumen que consume Reportes.
     * @return un conteo por cada estado presente
     */
    @Query("SELECT v.status AS status, COUNT(v) AS total FROM VehicleModel v "
            + "WHERE v.deleted = false GROUP BY v.status")
    List<StatusCount> countByStatus();
}
