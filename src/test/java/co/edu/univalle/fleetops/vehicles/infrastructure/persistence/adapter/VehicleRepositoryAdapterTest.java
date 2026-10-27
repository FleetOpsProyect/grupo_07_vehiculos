package co.edu.univalle.fleetops.vehicles.infrastructure.persistence.adapter;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.NOW;
import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.details;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.univalle.fleetops.vehicles.infrastructure.support.PostgresIntegrationTest;
import co.edu.univalle.fleetops.vehicles.application.port.out.VehicleRepository;
import co.edu.univalle.fleetops.vehicles.application.result.PageResult;
import co.edu.univalle.fleetops.vehicles.domain.exception.PlateAlreadyExistsException;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class VehicleRepositoryAdapterTest extends PostgresIntegrationTest {

    @Autowired
    private VehicleRepository repository;

    private Vehicle newVehicle(String plate, VehicleType type) {
        return Vehicle.register(plate, type, details(), new BigDecimal("15200"), NOW);
    }

    @Test
    void save_newVehicle_isFoundByIdWithSameData() {
        Vehicle vehicle = newVehicle("ABC123", VehicleType.PICKUP_TRUCK);

        repository.save(vehicle);
        Vehicle found = repository.findById(vehicle.id()).orElseThrow();

        assertThat(found.plate()).isEqualTo("ABC123");
        assertThat(found.type()).isEqualTo(VehicleType.PICKUP_TRUCK);
        assertThat(found.details()).isEqualTo(details());
        assertThat(found.odometerKm()).isEqualByComparingTo("15200.0");
        assertThat(found.status()).isEqualTo(VehicleStatus.ACTIVE);
        assertThat(found.blocks()).isEmpty();
        assertThat(found.isDeleted()).isFalse();
        assertThat(found.createdAt().toInstant()).isEqualTo(NOW.toInstant());
    }

    @Test
    void findById_unknownId_isEmpty() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void save_existingVehicleWithMaintenanceBlock_persistsBlockAndStatus() {
        Vehicle vehicle = newVehicle("ABC123", VehicleType.CAR);
        repository.save(vehicle);
        UUID maintenanceId = UUID.randomUUID();
        vehicle.startMaintenance(maintenanceId, NOW.plusHours(1));

        repository.save(vehicle);
        Vehicle found = repository.findById(vehicle.id()).orElseThrow();

        assertThat(found.status()).isEqualTo(VehicleStatus.IN_MAINTENANCE);
        assertThat(found.blocks()).singleElement().satisfies(block -> {
            assertThat(block.blockKey()).isEqualTo("MAINTENANCE:" + maintenanceId);
            assertThat(block.reason()).isEqualTo("PREVENTIVE");
        });
        assertThat(found.updatedAt().toInstant()).isEqualTo(NOW.plusHours(1).toInstant());
    }

    @Test
    void save_sameVehicleTwice_doesNotDuplicateBlocks() {
        Vehicle vehicle = newVehicle("ABC123", VehicleType.CAR);
        vehicle.startMaintenance(UUID.randomUUID(), NOW);
        repository.save(vehicle);

        repository.save(vehicle);

        assertThat(repository.findById(vehicle.id()).orElseThrow().blocks()).hasSize(1);
    }

    @Test
    void save_removedBlock_isDeletedFromDatabase() {
        Vehicle vehicle = newVehicle("ABC123", VehicleType.CAR);
        vehicle.startMaintenance(UUID.randomUUID(), NOW);
        repository.save(vehicle);
        Vehicle withoutBlocks = Vehicle.restore(vehicle.id(), vehicle.plate(), vehicle.type(), vehicle.details(),
                vehicle.odometerKm(), VehicleStatus.ACTIVE, java.util.List.of(), null, false,
                vehicle.createdAt(), NOW.plusDays(1));

        repository.save(withoutBlocks);

        assertThat(repository.findById(vehicle.id()).orElseThrow().blocks()).isEmpty();
    }

    @Test
    void save_duplicatedActivePlate_throwsPlateAlreadyExists() {
        repository.save(newVehicle("ABC123", VehicleType.CAR));
        Vehicle duplicate = newVehicle("ABC123", VehicleType.CAR);

        assertThatThrownBy(() -> repository.save(duplicate)).isInstanceOf(PlateAlreadyExistsException.class);
    }

    @Test
    void existsActiveByPlate_deletedVehicleReleasesThePlate() {
        Vehicle vehicle = newVehicle("ABC123", VehicleType.CAR);
        repository.save(vehicle);
        assertThat(repository.existsActiveByPlate("ABC123")).isTrue();

        vehicle.markDeleted(NOW);
        repository.save(vehicle);

        assertThat(repository.existsActiveByPlate("ABC123")).isFalse();
        assertThat(repository.save(newVehicle("ABC123", VehicleType.CAR)).plate()).isEqualTo("ABC123");
    }

    @Test
    void search_withoutStatus_excludesDeletedAndPaginates() {
        repository.save(newVehicle("AAA111", VehicleType.CAR));
        repository.save(newVehicle("BBB222", VehicleType.PICKUP_TRUCK));
        repository.save(newVehicle("CCC333", VehicleType.CAR));
        Vehicle deleted = newVehicle("DDD444", VehicleType.CAR);
        deleted.markDeleted(NOW);
        repository.save(deleted);

        PageResult<Vehicle> firstPage = repository.search(null, null, 0, 2);
        PageResult<Vehicle> cars = repository.search(null, VehicleType.CAR, 0, 10);

        assertThat(firstPage.content()).hasSize(2);
        assertThat(firstPage.total()).isEqualTo(3);
        assertThat(firstPage.page()).isZero();
        assertThat(firstPage.size()).isEqualTo(2);
        assertThat(cars.content()).extracting(Vehicle::plate).containsExactlyInAnyOrder("AAA111", "CCC333");
    }

    @Test
    void search_withStatus_filtersByThatStatus() {
        Vehicle inMaintenance = newVehicle("AAA111", VehicleType.CAR);
        inMaintenance.startMaintenance(UUID.randomUUID(), NOW);
        repository.save(inMaintenance);
        repository.save(newVehicle("BBB222", VehicleType.CAR));

        PageResult<Vehicle> result = repository.search(VehicleStatus.IN_MAINTENANCE, null, 0, 10);

        assertThat(result.content()).singleElement().satisfies(v -> {
            assertThat(v.plate()).isEqualTo("AAA111");
            assertThat(v.blocks()).hasSize(1);
        });
    }

    @Test
    void countByStatus_excludesDeleted() {
        Vehicle inMaintenance = newVehicle("AAA111", VehicleType.CAR);
        inMaintenance.startMaintenance(UUID.randomUUID(), NOW);
        repository.save(inMaintenance);
        repository.save(newVehicle("BBB222", VehicleType.CAR));
        repository.save(newVehicle("CCC333", VehicleType.CAR));
        Vehicle deleted = newVehicle("DDD444", VehicleType.CAR);
        deleted.markDeleted(NOW);
        repository.save(deleted);

        assertThat(repository.countByStatus()).containsOnly(
                java.util.Map.entry(VehicleStatus.ACTIVE, 2L), java.util.Map.entry(VehicleStatus.IN_MAINTENANCE, 1L));
    }

    @Test
    void findActiveByType_returnsOnlyActiveOfThatType() {
        Vehicle inMaintenance = newVehicle("AAA111", VehicleType.PICKUP_TRUCK);
        inMaintenance.startMaintenance(UUID.randomUUID(), NOW);
        repository.save(inMaintenance);
        repository.save(newVehicle("BBB222", VehicleType.PICKUP_TRUCK));
        repository.save(newVehicle("CCC333", VehicleType.CAR));

        assertThat(repository.findActiveByType(VehicleType.PICKUP_TRUCK)).extracting(Vehicle::plate)
                .containsExactly("BBB222");
        assertThat(repository.findActiveByType(null)).hasSize(2);
    }
}
