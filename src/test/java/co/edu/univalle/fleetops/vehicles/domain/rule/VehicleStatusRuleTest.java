package co.edu.univalle.fleetops.vehicles.domain.rule;

import static co.edu.univalle.fleetops.vehicles.application.support.VehicleFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import co.edu.univalle.fleetops.vehicles.domain.model.BlockType;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleBlock;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VehicleStatusRuleTest {

    private static VehicleBlock block(BlockType type) {
        return VehicleBlock.of(type, "x", null, NOW);
    }

    @Test
    void calculate_deletedWinsOverEverything() {
        var all = List.of(block(BlockType.INCIDENT), block(BlockType.MAINTENANCE), block(BlockType.DOCUMENT));

        assertThat(VehicleStatusRule.calculate(true, all, UUID.randomUUID())).isEqualTo(VehicleStatus.DELETED);
    }

    @Test
    void calculate_incidentWinsOverMaintenanceAndDocument() {
        var blocks = List.of(block(BlockType.DOCUMENT), block(BlockType.MAINTENANCE), block(BlockType.INCIDENT));

        assertThat(VehicleStatusRule.calculate(false, blocks, UUID.randomUUID())).isEqualTo(VehicleStatus.BLOCKED);
    }

    @Test
    void calculate_maintenanceWinsOverDocument() {
        var blocks = List.of(block(BlockType.DOCUMENT), block(BlockType.MAINTENANCE));

        assertThat(VehicleStatusRule.calculate(false, blocks, null)).isEqualTo(VehicleStatus.IN_MAINTENANCE);
    }

    @Test
    void calculate_documentWinsOverRoute() {
        var blocks = List.of(block(BlockType.DOCUMENT));

        assertThat(VehicleStatusRule.calculate(false, blocks, UUID.randomUUID()))
                .isEqualTo(VehicleStatus.INACTIVE_EXPIRED_DOCUMENTS);
    }

    @Test
    void calculate_routeWithoutBlocks_isOnRoute() {
        assertThat(VehicleStatusRule.calculate(false, List.of(), UUID.randomUUID()))
                .isEqualTo(VehicleStatus.ON_ROUTE);
    }

    @Test
    void calculate_noBlocksNoRoute_isActive() {
        assertThat(VehicleStatusRule.calculate(false, List.of(), null)).isEqualTo(VehicleStatus.ACTIVE);
    }
}
