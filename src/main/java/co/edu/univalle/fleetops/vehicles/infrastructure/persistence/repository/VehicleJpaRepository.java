package co.edu.univalle.fleetops.vehicles.infrastructure.persistence.repository;

import co.edu.univalle.fleetops.vehicles.infrastructure.persistence.entity.VehicleEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** Acceso Spring Data a la tabla {@code vehicle}. */
public interface VehicleJpaRepository
        extends JpaRepository<VehicleEntity, UUID>, JpaSpecificationExecutor<VehicleEntity> {

    /**
     * Indica si hay un vehículo no eliminado con esa placa.
     *
     * @param plate placa normalizada
     * @return {@code true} si la placa está en uso
     */
    boolean existsByPlateAndDeletedFalse(String plate);

    /**
     * Cuenta los vehículos no eliminados por estado.
     *
     * @return filas {@code [estado, cantidad]}
     */
    @Query("select v.status, count(v) from VehicleEntity v where v.deleted = false group by v.status")
    List<Object[]> countGroupedByStatus();
}
