package co.edu.univalle.fleetops.vehicles.infrastructure.web.mapper;

import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.BlockResponse;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.ChangeStatusRequest;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.CreateVehicleRequest;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.DeletedVehicleResponse;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.UpdateVehicleRequest;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.VehiclePageResponse;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.VehicleResponse;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.VehicleSummaryResponse;
import co.edu.univalle.fleetops.vehicles.application.command.ChangeToMaintenanceCommand;
import co.edu.univalle.fleetops.vehicles.application.command.RegisterVehicleCommand;
import co.edu.univalle.fleetops.vehicles.application.command.UpdateVehicleCommand;
import co.edu.univalle.fleetops.vehicles.application.query.ListVehiclesQuery;
import co.edu.univalle.fleetops.vehicles.application.result.BlockResult;
import co.edu.univalle.fleetops.vehicles.application.result.DeletedVehicleResult;
import co.edu.univalle.fleetops.vehicles.application.result.VehiclePageResult;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleResult;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleSummaryResult;
import co.edu.univalle.fleetops.vehicles.domain.exception.FieldError;
import co.edu.univalle.fleetops.vehicles.domain.exception.ValidationException;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Convierte entre los DTO HTTP y los comandos y resultados de la aplicación. */
public final class VehicleWebMapper {

    private static final ZoneOffset GMT_MINUS_5 = ZoneOffset.of("-05:00");
    private static final String REQUIRED = "REQUIRED";
    private static final String INVALID_FORMAT = "INVALID_FORMAT";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final List<String> NON_EDITABLE_FIELDS = List.of(
        "plate", "vehicle_type", "odometer_km", "status");

    private VehicleWebMapper() {
    }

    /**
     * Convierte el cuerpo de POST /vehicles en un comando.
     *
     * @param request cuerpo recibido
     * @return el comando para el caso de uso
     */
    public static RegisterVehicleCommand toCommand(CreateVehicleRequest request) {
        return new RegisterVehicleCommand(request.plate(), request.vehicleType(), request.brand(),
                request.model(), request.year(), request.color(), request.notes(), request.odometerKm());
    }

    /**
     * Convierte el cuerpo de PUT /vehicles/{id}/status en un comando. Un {@code status} que no es un
     * estado conocido es un error de formato; si es un estado válido distinto de IN_MAINTENANCE, el
     * caso de uso responde 422 después de comprobar que el vehículo existe.
     *
     * @param vehicleId id del vehículo, ya validado como UUID
     * @param request cuerpo recibido
     * @return el comando para el caso de uso
     * @throws ValidationException con todos los campos inválidos del cuerpo
     */
    public static ChangeToMaintenanceCommand toCommand(UUID vehicleId, ChangeStatusRequest request) {
        List<FieldError> errors = new ArrayList<>();
        VehicleStatus status = parseStatus(request.status(), errors);
        UUID maintenanceId = parseMaintenanceId(request.maintenanceId(), errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new ChangeToMaintenanceCommand(vehicleId, status, maintenanceId);
    }

    /**
     * Convierte el cuerpo de PUT /vehicles/{id} en un comando. Si el body incluye campos no editables
     * ({@code plate}, {@code vehicle_type}, {@code odometer_km}, {@code status}), responde 400
     * {@code VALIDATION_ERROR} con {@code NOT_EDITABLE} por cada uno.
     *
     * @param vehicleId id del vehículo, ya validado como UUID
     * @param request cuerpo recibido
     * @return el comando para el caso de uso
     * @throws ValidationException si se enviaron campos no editables
     */
    public static UpdateVehicleCommand toCommand(UUID vehicleId, UpdateVehicleRequest request) {
        List<FieldError> errors = new ArrayList<>();
        for (String forbiddenField : NON_EDITABLE_FIELDS) {
            if (request.extraFields().containsKey(forbiddenField)) {
                errors.add(new FieldError(forbiddenField, "NOT_EDITABLE"));
            }
        }
        return new UpdateVehicleCommand(vehicleId, request.brand(), request.model(), request.year(),
                request.color(), request.notes(), List.copyOf(errors));
    }

    /**
     * Convierte los parámetros de GET /vehicles en una consulta. Los parámetros llegan como texto para
     * reportar juntos todos los que no tienen formato válido; el caso de uso suma los errores de rango
     * (page, size) y lanza un único {@code VALIDATION_ERROR}. Un parámetro ausente o vacío toma su valor
     * por defecto: sin filtro para {@code status} y {@code type}, 0 para {@code page} y 20 para {@code size}.
     *
     * @param status estado recibido, o {@code null}
     * @param type tipo recibido, o {@code null}
     * @param page página recibida, o {@code null}
     * @param size tamaño de página recibido, o {@code null}
     * @return la consulta con los errores de formato encontrados
     */
    public static ListVehiclesQuery toQuery(String status, String type, String page, String size) {
        List<FieldError> errors = new ArrayList<>();
        VehicleStatus parsedStatus = parseOptionalEnum(status, VehicleStatus.class, "status", errors);
        VehicleType parsedType = parseOptionalEnum(type, VehicleType.class, "type", errors);
        int parsedPage = parseOptionalInt(page, DEFAULT_PAGE, "page", errors);
        int parsedSize = parseOptionalInt(size, DEFAULT_SIZE, "size", errors);
        return new ListVehiclesQuery(parsedStatus, parsedType, parsedPage, parsedSize, List.copyOf(errors));
    }

    /**
     * Convierte la página del caso de uso en la respuesta HTTP de GET /vehicles.
     *
     * @param result página calculada por el caso de uso
     * @return el DTO de respuesta
     */
    public static VehiclePageResponse toPageResponse(VehiclePageResult result) {
        return new VehiclePageResponse(result.content().stream().map(VehicleWebMapper::toSummaryResponse).toList(),
                result.page(), result.size(), result.total(), result.summary());
    }

    /**
     * Convierte el resultado del caso de uso en la respuesta HTTP, con fechas en GMT-5.
     *
     * @param result resultado del caso de uso
     * @return el DTO de respuesta
     */
    public static VehicleResponse toResponse(VehicleResult result) {
        return new VehicleResponse(result.vehicleId(), result.plate(), result.vehicleType(), result.brand(),
                result.model(), result.year(), result.color(), result.notes(), result.odometerKm(),
                result.status(), result.activeBlocks().stream().map(VehicleWebMapper::toBlockResponse).toList(),
                result.activeRouteId(), result.deleted(), inBogota(result.createdAt()),
                inBogota(result.updatedAt()));
    }

    /**
     * Convierte el resultado de la baja en la respuesta HTTP, con la fecha en GMT-5.
     *
     * @param result resultado del caso de uso de baja
     * @return el DTO de respuesta
     */
    public static DeletedVehicleResponse toDeletedResponse(DeletedVehicleResult result) {
        return new DeletedVehicleResponse(result.vehicleId(), result.plate(), result.status(), result.deleted(),
                inBogota(result.updatedAt()));
    }

    /**
     * Convierte un texto en UUID exigiendo el formato canónico completo (8-4-4-4-12). Es la única
     * validación de UUID de la capa REST: {@link UUID#fromString(String)} por sí sola acepta textos
     * abreviados como {@code 1-2-3-4-5} y los normaliza.
     *
     * @param raw texto recibido en la ruta o en el cuerpo
     * @return el UUID, con mayúsculas o minúsculas
     * @throws IllegalArgumentException si el texto es nulo o no tiene el formato canónico
     */
    public static UUID parseUuid(String raw) {
        if (raw == null || !CANONICAL_UUID.matcher(raw).matches()) {
            throw new IllegalArgumentException("Not a canonical UUID");
        }
        return UUID.fromString(raw);
    }

    private static VehicleStatus parseStatus(String raw, List<FieldError> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add(new FieldError("status", REQUIRED));
            return null;
        }
        try {
            return VehicleStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            errors.add(new FieldError("status", INVALID_FORMAT));
            return null;
        }
    }

    private static UUID parseMaintenanceId(String raw, List<FieldError> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add(new FieldError("maintenance_id", REQUIRED));
            return null;
        }
        try {
            return parseUuid(raw);
        } catch (IllegalArgumentException e) {
            errors.add(new FieldError("maintenance_id", INVALID_FORMAT));
            return null;
        }
    }

    private static <E extends Enum<E>> E parseOptionalEnum(String raw, Class<E> type, String field,
            List<FieldError> errors) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException e) {
            errors.add(new FieldError(field, INVALID_FORMAT));
            return null;
        }
    }

    private static int parseOptionalInt(String raw, int defaultValue, String field, List<FieldError> errors) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            errors.add(new FieldError(field, INVALID_FORMAT));
            return defaultValue;
        }
    }

    private static VehicleSummaryResponse toSummaryResponse(VehicleSummaryResult row) {
        return new VehicleSummaryResponse(row.vehicleId(), row.plate(), row.vehicleType(), row.brand(),
                row.model(), row.year(), row.odometerKm(), row.status());
    }

    private static BlockResponse toBlockResponse(BlockResult block) {
        return new BlockResponse(block.blockKey(), block.type(), block.sourceId(), block.reason(),
                inBogota(block.createdAt()));
    }

    private static OffsetDateTime inBogota(OffsetDateTime value) {
        return value.withOffsetSameInstant(GMT_MINUS_5);
    }
}