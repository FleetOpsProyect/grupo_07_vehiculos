package co.edu.univalle.fleetops.vehicles.infrastructure.web.mapper;

import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.BlockResponse;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.CreateVehicleRequest;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.VehicleResponse;
import co.edu.univalle.fleetops.vehicles.application.command.RegisterVehicleCommand;
import co.edu.univalle.fleetops.vehicles.application.result.BlockResult;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleResult;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Convierte entre los DTO HTTP y los comandos y resultados de la aplicación. */
public final class VehicleWebMapper {

    private static final ZoneOffset GMT_MINUS_5 = ZoneOffset.of("-05:00");

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

    private static BlockResponse toBlockResponse(BlockResult block) {
        return new BlockResponse(block.blockKey(), block.type(), block.sourceId(), block.reason(),
                inBogota(block.createdAt()));
    }

    private static OffsetDateTime inBogota(OffsetDateTime value) {
        return value.withOffsetSameInstant(GMT_MINUS_5);
    }
}
