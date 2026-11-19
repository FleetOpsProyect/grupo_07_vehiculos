package co.edu.univalle.fleetops.vehicles.infrastructure.persistence.adapter;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.activeVehicle;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import co.edu.univalle.fleetops.vehicles.application.exception.ConcurrentVehicleUpdateException;
import co.edu.univalle.fleetops.vehicles.domain.exception.PlateAlreadyExistsException;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.infrastructure.persistence.entity.VehicleEntity;
import co.edu.univalle.fleetops.vehicles.infrastructure.persistence.mapper.VehicleMapper;
import co.edu.univalle.fleetops.vehicles.infrastructure.persistence.repository.VehicleJpaRepository;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class VehicleRepositoryAdapterConcurrencyTest {

    @Mock
    private VehicleJpaRepository jpa;

    private VehicleRepositoryAdapter adapter;
    private final Vehicle vehicle = activeVehicle();

    @BeforeEach
    void setUp() {
        adapter = new VehicleRepositoryAdapter(jpa, new VehicleMapper());
        when(jpa.findById(vehicle.id())).thenReturn(Optional.of(new VehicleEntity()));
    }

    private static DataIntegrityViolationException violationOf(String constraint) {
        var cause = new ConstraintViolationException("duplicate key", new SQLException("duplicate key"), "insert",
                constraint);
        return new DataIntegrityViolationException("could not execute statement", cause);
    }

    @Test
    void save_staleVersion_throwsConcurrentUpdateWithTheOriginalCause() {
        var stale = new ObjectOptimisticLockingFailureException(VehicleEntity.class, vehicle.id());
        when(jpa.saveAndFlush(any(VehicleEntity.class))).thenThrow(stale);

        assertThatThrownBy(() -> adapter.save(vehicle))
                .isInstanceOf(ConcurrentVehicleUpdateException.class)
                .hasCause(stale);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pk_vehicle_block", "uq_vehicle_block_key", "PK_VEHICLE_BLOCK"})
    void save_blockAlreadyInsertedByAnotherRequest_throwsConcurrentUpdate(String constraint) {
        DataIntegrityViolationException duplicate = violationOf(constraint);
        when(jpa.saveAndFlush(any(VehicleEntity.class))).thenThrow(duplicate);

        assertThatThrownBy(() -> adapter.save(vehicle))
                .isInstanceOf(ConcurrentVehicleUpdateException.class)
                .hasCause(duplicate);
    }

    @Test
    void save_duplicatedPlate_stillThrowsPlateAlreadyExists() {
        when(jpa.saveAndFlush(any(VehicleEntity.class))).thenThrow(violationOf("ux_vehicle_plate_active"));

        assertThatThrownBy(() -> adapter.save(vehicle)).isInstanceOf(PlateAlreadyExistsException.class);
    }

    @Test
    void save_anyOtherIntegrityViolation_isNotHiddenAsAConcurrencyConflict() {
        DataIntegrityViolationException other = violationOf("ck_vehicle_year");
        when(jpa.saveAndFlush(any(VehicleEntity.class))).thenThrow(other);

        assertThatThrownBy(() -> adapter.save(vehicle)).isSameAs(other);
    }

    @Test
    void save_integrityViolationWithoutConstraintName_isRethrownUnchanged() {
        DataIntegrityViolationException unnamed = new DataIntegrityViolationException("no cause");
        when(jpa.saveAndFlush(any(VehicleEntity.class))).thenThrow(unnamed);

        assertThatThrownBy(() -> adapter.save(vehicle)).isSameAs(unnamed);
    }
}
