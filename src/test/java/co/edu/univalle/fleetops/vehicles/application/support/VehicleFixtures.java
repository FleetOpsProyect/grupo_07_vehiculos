package co.edu.univalle.fleetops.vehicles.application.support;

import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleDetails;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Datos de prueba compartidos: reloj fijo en GMT-5 (2026-10-27 10:30) y vehículos de ejemplo. */
public final class VehicleFixtures {

    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T15:30:00Z"),
            ZoneId.of("America/Bogota"));
    public static final OffsetDateTime NOW = OffsetDateTime.now(CLOCK);

    private VehicleFixtures() {
    }

    /** @return datos operativos válidos de ejemplo */
    public static VehicleDetails details() {
        return new VehicleDetails("Toyota", "Hilux", 2023, "White", "Assigned to Cali branch");
    }

    /** @return una camioneta ACTIVE registrada hace 30 días */
    public static Vehicle activeVehicle() {
        return Vehicle.register("ABC123", VehicleType.PICKUP_TRUCK, details(), new BigDecimal("15200"),
                NOW.minusDays(30));
    }
}
