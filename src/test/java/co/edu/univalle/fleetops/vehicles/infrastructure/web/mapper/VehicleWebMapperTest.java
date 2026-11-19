package co.edu.univalle.fleetops.vehicles.infrastructure.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.univalle.fleetops.vehicles.application.command.ChangeToMaintenanceCommand;
import co.edu.univalle.fleetops.vehicles.application.query.ListVehiclesQuery;
import co.edu.univalle.fleetops.vehicles.application.result.VehiclePageResult;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleSummaryResult;
import co.edu.univalle.fleetops.vehicles.domain.exception.FieldError;
import co.edu.univalle.fleetops.vehicles.domain.exception.ValidationException;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.ChangeStatusRequest;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.VehiclePageResponse;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "1-2-3-4-5", "1-1-1-1-1"})
    void toCommand_maintenanceIdThatIsNotCanonicalUuid_reportsInvalidFormat(String maintenanceId) {
        var request = new ChangeStatusRequest("IN_MAINTENANCE", maintenanceId);

        assertThatThrownBy(() -> VehicleWebMapper.toCommand(VEHICLE_ID, request))
                .isInstanceOfSatisfying(ValidationException.class, error -> assertThat(error.errors())
                        .containsExactly(new FieldError("maintenance_id", "INVALID_FORMAT")));
    }

    @Test
    void toCommand_uppercaseMaintenanceId_isValid() {
        var request = new ChangeStatusRequest("IN_MAINTENANCE", MAINTENANCE_ID.toString().toUpperCase());

        ChangeToMaintenanceCommand command = VehicleWebMapper.toCommand(VEHICLE_ID, request);

        assertThat(command.maintenanceId()).isEqualTo(MAINTENANCE_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"3fa85f64-5717-4562-b3fc-2c963f66afa6", "3FA85F64-5717-4562-B3FC-2C963F66AFA6",
        "00000000-0000-0000-0000-000000000000"})
    void parseUuid_canonicalFormat_returnsTheUuid(String raw) {
        assertThat(VehicleWebMapper.parseUuid(raw)).isEqualTo(UUID.fromString(raw));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1-2-3-4-5", "1-1-1-1-1", "not-a-uuid", " 3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "3fa85f64-5717-4562-b3fc-2c963f66afa6 ", "3fa85f64-5717-4562-b3fc-2c963f66afa6\n",
        "3fa85f6457174562b3fc2c963f66afa6", "3fa85f64-5717-4562-b3fc-2c963f66afa",
        "3fa85f64-5717-4562-b3fc-2c963f66afag"})
    void parseUuid_anyOtherText_throws(String raw) {
        assertThatThrownBy(() -> VehicleWebMapper.parseUuid(raw)).isInstanceOf(IllegalArgumentException.class);
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

    @Test
    void toQuery_noParameters_usesDefaultsWithoutFilters() {
        ListVehiclesQuery query = VehicleWebMapper.toQuery(null, null, null, null);

        assertThat(query).isEqualTo(new ListVehiclesQuery(null, null, 0, 20, List.of()));
    }

    @Test
    void toQuery_blankParameters_areTreatedAsMissing() {
        ListVehiclesQuery query = VehicleWebMapper.toQuery("", " ", "", " ");

        assertThat(query).isEqualTo(new ListVehiclesQuery(null, null, 0, 20, List.of()));
    }

    @Test
    void toQuery_validParameters_areParsed() {
        ListVehiclesQuery query = VehicleWebMapper.toQuery("IN_MAINTENANCE", "PICKUP_TRUCK", "2", "50");

        assertThat(query).isEqualTo(
                new ListVehiclesQuery(VehicleStatus.IN_MAINTENANCE, VehicleType.PICKUP_TRUCK, 2, 50, List.of()));
    }

    @Test
    void toQuery_deletedStatus_isParsedSoTheUseCaseRejectsIt() {
        ListVehiclesQuery query = VehicleWebMapper.toQuery("DELETED", null, null, null);

        assertThat(query.status()).isEqualTo(VehicleStatus.DELETED);
        assertThat(query.preValidationErrors()).isEmpty();
    }

    @Test
    void toQuery_everyParameterWithInvalidFormat_reportsAllOfThemAndKeepsDefaults() {
        ListVehiclesQuery query = VehicleWebMapper.toQuery("active", "PLANE", "abc", "1.5");

        assertThat(query.preValidationErrors()).containsExactly(new FieldError("status", "INVALID_FORMAT"),
                new FieldError("type", "INVALID_FORMAT"), new FieldError("page", "INVALID_FORMAT"),
                new FieldError("size", "INVALID_FORMAT"));
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(20);
    }

    @Test
    void toPageResponse_copiesRowsPagingAndSummaryInOrder() {
        UUID vehicleId = UUID.randomUUID();
        var row = new VehicleSummaryResult(vehicleId, "ABC123", VehicleType.CAR, "Toyota", "Corolla", 2022,
                new BigDecimal("15400.5"), VehicleStatus.ACTIVE);
        Map<VehicleStatus, Long> summary = new LinkedHashMap<>();
        summary.put(VehicleStatus.ACTIVE, 1L);
        summary.put(VehicleStatus.ON_ROUTE, 0L);

        VehiclePageResponse response = VehicleWebMapper.toPageResponse(
                new VehiclePageResult(List.of(row), 0, 20, 1, summary));

        assertThat(response.content()).singleElement().satisfies(item -> {
            assertThat(item.vehicleId()).isEqualTo(vehicleId);
            assertThat(item.plate()).isEqualTo("ABC123");
            assertThat(item.vehicleType()).isEqualTo(VehicleType.CAR);
            assertThat(item.brand()).isEqualTo("Toyota");
            assertThat(item.model()).isEqualTo("Corolla");
            assertThat(item.year()).isEqualTo(2022);
            assertThat(item.odometerKm()).isEqualByComparingTo("15400.5");
            assertThat(item.status()).isEqualTo(VehicleStatus.ACTIVE);
        });
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.summary()).containsExactly(Map.entry(VehicleStatus.ACTIVE, 1L),
                Map.entry(VehicleStatus.ON_ROUTE, 0L));
    }
}