package co.edu.univalle.fleetops.vehicles.infrastructure.persistence.repository;

import co.edu.univalle.fleetops.vehicles.infrastructure.persistence.entity.StatusHistoryEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso Spring Data a la tabla {@code status_history}. */
public interface StatusHistoryJpaRepository extends JpaRepository<StatusHistoryEntity, UUID> {
}
