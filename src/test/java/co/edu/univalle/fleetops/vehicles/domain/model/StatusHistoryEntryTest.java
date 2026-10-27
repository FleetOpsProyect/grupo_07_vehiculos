package co.edu.univalle.fleetops.vehicles.domain.model;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatusHistoryEntryTest {

    @Test
    void of_generatesIdAndKeepsFields() {
        UUID vehicleId = UUID.randomUUID();

        StatusHistoryEntry entry = StatusHistoryEntry.of(vehicleId, VehicleStatus.ACTIVE,
                VehicleStatus.IN_MAINTENANCE, StatusChangeCause.MAINTENANCE_STARTED, "m-1", NOW);

        assertThat(entry.id()).isNotNull();
        assertThat(entry.vehicleId()).isEqualTo(vehicleId);
        assertThat(entry.fromStatus()).isEqualTo(VehicleStatus.ACTIVE);
        assertThat(entry.toStatus()).isEqualTo(VehicleStatus.IN_MAINTENANCE);
        assertThat(entry.cause()).isEqualTo(StatusChangeCause.MAINTENANCE_STARTED);
        assertThat(entry.sourceId()).isEqualTo("m-1");
        assertThat(entry.changedAt()).isEqualTo(NOW);
    }
}
