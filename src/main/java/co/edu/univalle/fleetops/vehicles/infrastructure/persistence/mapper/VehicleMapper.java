package co.edu.univalle.fleetops.vehicles.infrastructure.persistence.mapper;

import co.edu.univalle.fleetops.vehicles.infrastructure.persistence.entity.VehicleBlockEntity;
import co.edu.univalle.fleetops.vehicles.infrastructure.persistence.entity.VehicleEntity;
import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleBlock;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleDetails;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Convierte entre el vehículo del dominio y sus filas JPA. */
@Component
public class VehicleMapper {

    /**
     * Reconstruye el vehículo del dominio desde su fila.
     *
     * @param entity fila con sus bloqueos
     * @return el vehículo del dominio
     */
    public Vehicle toDomain(VehicleEntity entity) {
        VehicleDetails details = new VehicleDetails(entity.getBrand(), entity.getModel(), entity.getYear(),
                entity.getColor(), entity.getNotes());
        return Vehicle.restore(entity.getId(), entity.getPlate(), entity.getVehicleType(), details,
                entity.getOdometerKm(), entity.getStatus(),
                entity.getBlocks().stream().map(VehicleMapper::toDomainBlock).toList(),
                entity.getActiveRouteId(), entity.isDeleted(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    /**
     * Copia el estado del vehículo del dominio sobre una fila nueva o ya cargada. Conserva la
     * {@code version} de la fila para que JPA aplique el bloqueo optimista, y sincroniza los
     * bloqueos por su clave.
     *
     * @param vehicle vehículo del dominio
     * @param entity fila destino
     */
    public void copyTo(Vehicle vehicle, VehicleEntity entity) {
        entity.setId(vehicle.id());
        entity.setPlate(vehicle.plate());
        entity.setVehicleType(vehicle.type());
        entity.setBrand(vehicle.details().brand());
        entity.setModel(vehicle.details().model());
        entity.setYear(vehicle.details().year());
        entity.setColor(vehicle.details().color());
        entity.setNotes(vehicle.details().notes());
        entity.setOdometerKm(vehicle.odometerKm());
        entity.setStatus(vehicle.status());
        entity.setActiveRouteId(vehicle.activeRouteId());
        entity.setDeleted(vehicle.isDeleted());
        entity.setCreatedAt(vehicle.createdAt());
        entity.setUpdatedAt(vehicle.updatedAt());
        syncBlocks(vehicle, entity);
    }

    private void syncBlocks(Vehicle vehicle, VehicleEntity entity) {
        Set<String> wanted = vehicle.blocks().stream().map(VehicleBlock::blockKey).collect(Collectors.toSet());
        entity.getBlocks().removeIf(block -> !wanted.contains(block.getBlockKey()));
        Set<String> stored = entity.getBlocks().stream().map(VehicleBlockEntity::getBlockKey)
                .collect(Collectors.toSet());
        vehicle.blocks().stream().filter(block -> !stored.contains(block.blockKey()))
                .forEach(block -> entity.getBlocks().add(toBlockEntity(block, entity)));
    }

    private static VehicleBlockEntity toBlockEntity(VehicleBlock block, VehicleEntity vehicle) {
        VehicleBlockEntity entity = new VehicleBlockEntity();
        entity.setId(UUID.nameUUIDFromBytes((vehicle.getId() + "|" + block.blockKey())
                .getBytes(StandardCharsets.UTF_8)));
        entity.setVehicle(vehicle);
        entity.setBlockKey(block.blockKey());
        entity.setBlockType(block.type());
        entity.setSourceId(block.sourceId());
        entity.setReason(block.reason());
        entity.setCreatedAt(block.createdAt());
        return entity;
    }

    private static VehicleBlock toDomainBlock(VehicleBlockEntity entity) {
        return new VehicleBlock(entity.getBlockKey(), entity.getBlockType(), entity.getSourceId(),
                entity.getReason(), entity.getCreatedAt());
    }
}
