package co.edu.univalle.fleetops.vehicles.repository;

import co.edu.univalle.fleetops.vehicles.model.AppliedRouteModel;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repositorio JPA de rutas ya aplicadas al odómetro. */
@Repository
public interface AppliedRouteRepository extends JpaRepository<AppliedRouteModel, UUID> {
}
