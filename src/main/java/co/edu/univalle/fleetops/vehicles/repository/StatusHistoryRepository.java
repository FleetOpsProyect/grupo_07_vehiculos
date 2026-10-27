package co.edu.univalle.fleetops.vehicles.repository;

import co.edu.univalle.fleetops.vehicles.model.StatusHistoryModel;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repositorio JPA del historial de estados. */
@Repository
public interface StatusHistoryRepository extends JpaRepository<StatusHistoryModel, UUID> {

    /**
     * Historial de un vehículo, del cambio más reciente al más antiguo.
     * @param vehicleId id del vehículo
     * @return cambios de estado
     */
    List<StatusHistoryModel> findAllByVehicleIdOrderByChangedAtDesc(UUID vehicleId);
}
