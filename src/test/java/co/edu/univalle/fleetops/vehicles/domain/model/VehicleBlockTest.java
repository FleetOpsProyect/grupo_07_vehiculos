package co.edu.univalle.fleetops.vehicles.domain.model;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VehicleBlockTest {

    @Test
    void of_buildsDeterministicKey() {
        VehicleBlock block = VehicleBlock.of(BlockType.INCIDENT, "inc-9d2f", "MECHANICAL_FAILURE", NOW);

        assertThat(block.blockKey()).isEqualTo("INCIDENT:inc-9d2f");
        assertThat(block.type()).isEqualTo(BlockType.INCIDENT);
        assertThat(block.sourceId()).isEqualTo("inc-9d2f");
        assertThat(block.reason()).isEqualTo("MECHANICAL_FAILURE");
        assertThat(block.createdAt()).isEqualTo(NOW);
    }
}
