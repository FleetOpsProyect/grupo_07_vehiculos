package co.edu.univalle.fleetops.vehicles.service;

import co.edu.univalle.fleetops.vehicles.dto.CreateVehicleRequest;
import co.edu.univalle.fleetops.vehicles.model.BlockType;
import co.edu.univalle.fleetops.vehicles.model.VehicleBlockModel;
import co.edu.univalle.fleetops.vehicles.model.VehicleModel;
import co.edu.univalle.fleetops.vehicles.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.model.VehicleType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.UUID;

// Datos de prueba compartidos por los tests unitarios
final class VehicleFixtures {

    // Reloj fijo: 26 de octubre de 2026 en Bogotá
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T17:00:00Z"), ZoneId.of("America/Bogota"));

    static final UUID VEHICLE_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

    private VehicleFixtures() {
    }

    // Vehículo ACTIVE sin bloqueos
    static VehicleModel activeVehicle() {
        return activeVehicle(VEHICLE_ID, "ABC123");
    }

    static VehicleModel activeVehicle(final UUID id, final String plate) {
        OffsetDateTime now = OffsetDateTime.now(CLOCK);
        return VehicleModel.builder()
                .id(id)
                .plate(plate)
                .vehicleType(VehicleType.PICKUP_TRUCK)
                .brand("Toyota")
                .model("Hilux")
                .year(2023)
                .odometerKm(new BigDecimal("15200.0"))
                .status(VehicleStatus.ACTIVE)
                .deleted(false)
                .version(1L)
                .createdAt(now)
                .updatedAt(now)
                .blocks(new HashSet<>())
                .build();
    }

    // Agrega un bloqueo y deja el estado derivado, como quedaría en la BD
    static VehicleModel withBlock(final VehicleModel vehicle, final BlockType type, final String sourceId) {
        vehicle.addBlock(VehicleBlockModel.builder()
                .blockKey(VehicleStatusRules.blockKey(type, sourceId))
                .blockType(type)
                .sourceId(sourceId)
                .createdAt(OffsetDateTime.now(CLOCK))
                .build());
        vehicle.setStatus(VehicleStatusRules.derive(vehicle));
        return vehicle;
    }

    // Petición de registro válida
    static CreateVehicleRequest validCreateRequest() {
        return new CreateVehicleRequest("abc-123", VehicleType.PICKUP_TRUCK, "Toyota", "Hilux",
                2023, "Blanco", "Sede Cali", new BigDecimal("15200"));
    }
}
