package co.edu.univalle.fleetops.vehicles.infrastructure.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.univalle.fleetops.vehicles.application.command.ChangeToMaintenanceCommand;
import co.edu.univalle.fleetops.vehicles.domain.exception.FieldError;
import co.edu.univalle.fleetops.vehicles.domain.exception.ValidationException;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.ChangeStatusRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class VehicleWebMapperTest {

    private static final UUID VEHICLE_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private static final UUID MAINTENANCE_ID = UUID.fromString("c56a4180-65aa-42ec-a945-5fd21dec0538");

    @Test
    void toCommand_validBody_buildsCommand() {
        var request = new ChangeStatusRequest("IN_MAINTENANCE", MAINTENANCE_ID.toString());

        ChangeToMaintenanceCommand command = VehicleWebMapper.toCommand(VEHICLE_ID, request);

        assertThat(command.vehicleId()).isEqualTo(VEHICLE_ID);
        assertThat(command.status()).isEqualTo(VehicleStatus.IN_MAINTENANCE);
        assertThat(command.maintenanceId()).isEqualTo(MAINTENANCE_ID);
    }

    @Test
    void toCommand_otherValidStatus_keepsItSoTheUseCaseRejectsIt() {
        var request = new ChangeStatusRequest("ACTIVE", MAINTENANCE_ID.toString());

        ChangeToMaintenanceCommand command = VehicleWebMapper.toCommand(VEHICLE_ID, request);

        assertThat(command.status()).isEqualTo(VehicleStatus.ACTIVE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void toCommand_missingStatus_reportsRequired(String status) {
        var request = new ChangeStatusRequest(status, MAINTENANCE_ID.toString());

        assertThatThrownBy(() -> VehicleWebMapper.toCommand(VEHICLE_ID, request))
                .isInstanceOfSatisfying(ValidationException.class, error -> assertThat(error.errors())
                        .containsExactly(new FieldError("status", "REQUIRED")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FOO", "in_maintenance", "IN MAINTENANCE"})
    void toCommand_unknownStatus_reportsInvalidFormat(String status) {
        var request = new ChangeStatusRequest(status, MAINTENANCE_ID.toString());

        assertThatThrownBy(() -> VehicleWebMapper.toCommand(VEHICLE_ID, request))
                .isInstanceOfSatisfying(ValidationException.class, error -> assertThat(error.errors())
                        .containsExactly(new FieldError("status", "INVALID_FORMAT")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void toCommand_missingMaintenanceId_reportsRequired(String maintenanceId) {
        var request = new ChangeStatusRequest("IN_MAINTENANCE", maintenanceId);

        assertThatThrownBy(() -> VehicleWebMapper.toCommand(VEHICLE_ID, request))
                .isInstanceOfSatisfying(ValidationException.class, error -> assertThat(error.errors())
                        .containsExactly(new FieldError("maintenance_id", "REQUIRED")));
    }

    @Test
    void toCommand_maintenanceIdThatIsNotUuid_reportsInvalidFormat() {
        var request = new ChangeStatusRequest("IN_MAINTENANCE", "not-a-uuid");

        assertThatThrownBy(() -> VehicleWebMapper.toCommand(VEHICLE_ID, request))
                .isInstanceOfSatisfying(ValidationException.class, error -> assertThat(error.errors())
                        .containsExactly(new FieldError("maintenance_id", "INVALID_FORMAT")));
    }

    @Test
    void toCommand_everyFieldInvalid_reportsAllOfThemAtOnce() {
        var request = new ChangeStatusRequest("FOO", "not-a-uuid");

        assertThatThrownBy(() -> VehicleWebMapper.toCommand(VEHICLE_ID, request))
                .isInstanceOfSatisfying(ValidationException.class, error -> assertThat(error.errors())
                        .containsExactlyInAnyOrder(new FieldError("status", "INVALID_FORMAT"),
                                new FieldError("maintenance_id", "INVALID_FORMAT")));
    }

    @Test
    void toCommand_emptyBody_reportsBothFieldsRequired() {
        var request = new ChangeStatusRequest(null, null);

        assertThatThrownBy(() -> VehicleWebMapper.toCommand(VEHICLE_ID, request))
                .isInstanceOfSatisfying(ValidationException.class, error -> assertThat(error.errors())
                        .containsExactlyInAnyOrder(new FieldError("status", "REQUIRED"),
                                new FieldError("maintenance_id", "REQUIRED")));
    }
}
