package co.edu.univalle.fleetops.vehicles.infrastructure.persistence.adapter;

import co.edu.univalle.fleetops.vehicles.infrastructure.persistence.entity.VehicleEntity;
import co.edu.univalle.fleetops.vehicles.infrastructure.persistence.mapper.VehicleMapper;
import co.edu.univalle.fleetops.vehicles.infrastructure.persistence.repository.VehicleJpaRepository;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.result.PageResult;
import co.edu.univalle.fleetops.vehicles.domain.exception.PlateAlreadyExistsException;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador JPA del puerto {@link VehicleRepository}. */
@Repository
public class VehicleRepositoryAdapter implements VehicleRepository {

    private static final String UNIQUE_PLATE_INDEX = "ux_vehicle_plate_active";
    private static final Sort STABLE_ORDER = Sort.by("createdAt", "id");

    private final VehicleJpaRepository jpa;
    private final VehicleMapper mapper;

    /**
     * Crea el adaptador.
     *
     * @param jpa repositorio Spring Data
     * @param mapper conversión dominio-JPA
     */
    public VehicleRepositoryAdapter(VehicleJpaRepository jpa, VehicleMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Vehicle save(Vehicle vehicle) {
        VehicleEntity entity = jpa.findById(vehicle.id()).orElseGet(VehicleEntity::new);
        mapper.copyTo(vehicle, entity);
        try {
            return mapper.toDomain(jpa.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            if (isPlateViolation(e)) {
                throw new PlateAlreadyExistsException(vehicle.plate());
            }
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Vehicle> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActiveByPlate(String plate) {
        return jpa.existsByPlateAndDeletedFalse(plate);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Vehicle> search(VehicleStatus status, VehicleType type, int page, int size) {
        Page<VehicleEntity> found = jpa.findAll(filter(status, type), PageRequest.of(page, size, STABLE_ORDER));
        return new PageResult<>(found.getContent().stream().map(mapper::toDomain).toList(), page, size,
                found.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<VehicleStatus, Long> countByStatus() {
        Map<VehicleStatus, Long> counts = new EnumMap<>(VehicleStatus.class);
        for (Object[] row : jpa.countGroupedByStatus()) {
            counts.put((VehicleStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> findActiveByType(VehicleType type) {
        return jpa.findAll(filter(VehicleStatus.ACTIVE, type), STABLE_ORDER).stream()
                .map(mapper::toDomain).toList();
    }

    /** Sin estado excluye los eliminados; con estado filtra solo por ese estado. */
    private static Specification<VehicleEntity> filter(VehicleStatus status, VehicleType type) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status == null) {
                predicates.add(builder.isFalse(root.get("deleted")));
            } else {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (type != null) {
                predicates.add(builder.equal(root.get("vehicleType"), type));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean isPlateViolation(DataIntegrityViolationException exception) {
        return exception.getCause() instanceof ConstraintViolationException violation
                && UNIQUE_PLATE_INDEX.equalsIgnoreCase(violation.getConstraintName());
    }
}
