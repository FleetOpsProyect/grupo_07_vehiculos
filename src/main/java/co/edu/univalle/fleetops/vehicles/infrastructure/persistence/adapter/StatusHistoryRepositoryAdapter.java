package co.edu.univalle.fleetops.vehicles.infrastructure.persistence.adapter;

import co.edu.univalle.fleetops.vehicles.infrastructure.persistence.mapper.StatusHistoryMapper;
import co.edu.univalle.fleetops.vehicles.infrastructure.persistence.repository.StatusHistoryJpaRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.StatusHistoryRepository;
import co.edu.univalle.fleetops.vehicles.domain.model.StatusHistoryEntry;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador JPA del puerto {@link StatusHistoryRepository}. */
@Repository
public class StatusHistoryRepositoryAdapter implements StatusHistoryRepository {

    private final StatusHistoryJpaRepository jpa;
    private final StatusHistoryMapper mapper;

    /**
     * Crea el adaptador.
     *
     * @param jpa repositorio Spring Data
     * @param mapper conversión dominio-JPA
     */
    public StatusHistoryRepositoryAdapter(StatusHistoryJpaRepository jpa, StatusHistoryMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void save(StatusHistoryEntry entry) {
        jpa.save(mapper.toEntity(entry));
    }
}
