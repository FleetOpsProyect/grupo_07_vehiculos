package co.edu.univalle.fleetops.vehicles.infrastructure.web.controller;

import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.ChangeStatusRequest;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.CreateVehicleRequest;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.DeletedVehicleResponse;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.UpdateVehicleRequest;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.VehiclePageResponse;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.VehicleResponse;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.error.ApiErrorResponse;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.error.InvalidVehicleIdException;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.mapper.VehicleWebMapper;
import co.edu.univalle.fleetops.vehicles.application.port.in.ChangeToMaintenanceUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.DeleteVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.GetVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.ListVehiclesUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.RegisterVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.UpdateVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de vehículos. Solo traduce HTTP a casos de uso: no tiene reglas de negocio.
 * Ejemplo de referencia para los endpoints que faltan.
 */
@RestController
@RequestMapping("/vehicles")
@Tag(name = "Vehicles")
public class VehicleController {

    private final RegisterVehicleUseCase registerVehicle;
    private final GetVehicleUseCase getVehicle;
    private final ListVehiclesUseCase listVehicles;
    private final UpdateVehicleUseCase updateVehicle;
    private final ChangeToMaintenanceUseCase changeToMaintenance;
    private final DeleteVehicleUseCase deleteVehicle;

    /**
     * Crea el controller con los puertos de entrada que usa.
     *
     * @param registerVehicle caso de uso de alta
     * @param getVehicle caso de uso de detalle
     * @param listVehicles caso de uso de listado
     * @param updateVehicle caso de uso de edicion
     * @param changeToMaintenance caso de uso de paso a mantenimiento
     * @param deleteVehicle caso de uso de baja lógica
     */
    public VehicleController(RegisterVehicleUseCase registerVehicle, GetVehicleUseCase getVehicle,
            ListVehiclesUseCase listVehicles, UpdateVehicleUseCase updateVehicle,
            ChangeToMaintenanceUseCase changeToMaintenance, DeleteVehicleUseCase deleteVehicle) {
        this.registerVehicle = registerVehicle;
        this.getVehicle = getVehicle;
        this.listVehicles = listVehicles;
        this.updateVehicle = updateVehicle;
        this.changeToMaintenance = changeToMaintenance;
        this.deleteVehicle = deleteVehicle;
    }

    /**
     * POST /vehicles: registra un vehículo en ACTIVE.
     *
     * @param request datos del alta
     * @return 201 con el vehículo y la cabecera {@code Location}
     */
    @PostMapping
    @Operation(summary = "Registra un vehículo; valida que la placa no exista y lo crea en ACTIVE")
    @ApiResponse(responseCode = "201", description = "Vehículo creado")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR")
    @ApiResponse(responseCode = "409", description = "PLATE_ALREADY_EXISTS")
    public ResponseEntity<VehicleResponse> register(@RequestBody CreateVehicleRequest request) {
        VehicleResult result = registerVehicle.execute(VehicleWebMapper.toCommand(request));
        return ResponseEntity.created(URI.create("/vehicles/" + result.vehicleId()))
                .body(VehicleWebMapper.toResponse(result));
    }

    /**
     * GET /vehicles: listado paginado con filtros opcionales y el conteo por estado de toda la flota.
     * Los parámetros llegan como texto para que un 400 informe todos los inválidos a la vez.
     *
     * @param status filtro opcional por estado; DELETED no se acepta
     * @param type filtro opcional por tipo
     * @param page página desde 0; por defecto 0
     * @param size tamaño de página de 1 a 100; por defecto 20
     * @return 200 con la página y el bloque {@code summary}
     */
    @GetMapping
    @Operation(summary = "Lista vehículos paginados, con filtros opcionales y el conteo por estado de la flota")
    @ApiResponse(responseCode = "200", description = "Página de vehículos con summary")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: status, type, page o size inválidos",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE: base de datos no disponible",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public VehiclePageResponse list(
            @Parameter(description = "ACTIVE, ON_ROUTE, BLOCKED, IN_MAINTENANCE o INACTIVE_EXPIRED_DOCUMENTS")
            @RequestParam(required = false) String status,
            @Parameter(description = "CAR, PICKUP_TRUCK o SPECIALIZED_VEHICLE")
            @RequestParam(required = false) String type,
            @Parameter(description = "Página desde 0 (por defecto 0)")
            @RequestParam(required = false) String page,
            @Parameter(description = "Tamaño de página de 1 a 100 (por defecto 20)")
            @RequestParam(required = false) String size) {
        return VehicleWebMapper.toPageResponse(
                listVehicles.execute(VehicleWebMapper.toQuery(status, type, page, size)));
    }

    /**
     * GET /vehicles/{id}: detalle de un vehículo. Un id que no es UUID responde 404.
     *
     * @param id id tal como llegó en la ruta
     * @return 200 con el vehículo
     */
    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un vehículo (estado, odómetro oficial, bloqueos)")
    @ApiResponse(responseCode = "200", description = "Vehículo encontrado")
    @ApiResponse(responseCode = "404", description = "VEHICLE_NOT_FOUND: no existe o el id no es UUID",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE: base de datos no disponible",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public VehicleResponse get(@PathVariable String id) {
        return VehicleWebMapper.toResponse(getVehicle.execute(parseId(id)));
    }

    /**
     * PUT /vehicles/{id}: reemplaza los campos editables de un vehículo. Un id que no es UUID responde 404.
     *
     * @param id id tal como llegó en la ruta
     * @param request datos a reemplazar
     * @return 200 con el vehículo completo
     */
    @PutMapping("/{id}")
    @Operation(summary = "Edita marca, modelo, año, color y observaciones de un vehículo")
    @ApiResponse(responseCode = "200", description = "Vehículo actualizado")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: campos inválidos o no editables",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "VEHICLE_NOT_FOUND: no existe, está eliminado o el id no es UUID",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE: base de datos no disponible",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public VehicleResponse update(@PathVariable String id, @RequestBody UpdateVehicleRequest request) {
        UUID vehicleId = parseId(id);
        return VehicleWebMapper.toResponse(updateVehicle.execute(VehicleWebMapper.toCommand(vehicleId, request)));
    }

    /**
     * PUT /vehicles/{id}/status: pasa el vehículo a IN_MAINTENANCE. Un id que no es UUID responde 404.
     * Repetir la petición con el mismo {@code maintenance_id} responde 200 sin cambios.
     *
     * @param id id tal como llegó en la ruta
     * @param request estado pedido y mantenimiento que origina el bloqueo
     * @return 200 con el vehículo completo
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "Pasa el vehículo a IN_MAINTENANCE, solo si estaba ACTIVE; es idempotente por "
            + "maintenance_id")
    @ApiResponse(responseCode = "200", description = "Vehículo en IN_MAINTENANCE, con el bloqueo en active_blocks")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: status o maintenance_id ausentes o inválidos")
    @ApiResponse(responseCode = "404", description = "VEHICLE_NOT_FOUND: no existe, está eliminado o el id no es UUID")
    @ApiResponse(responseCode = "409", description = "INVALID_STATUS_TRANSITION: el vehículo no estaba ACTIVE")
    @ApiResponse(responseCode = "422", description = "STATUS_NOT_ALLOWED: solo se acepta IN_MAINTENANCE")
    @ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE: base de datos no disponible")
    public VehicleResponse changeStatus(@PathVariable String id, @RequestBody ChangeStatusRequest request) {
        UUID vehicleId = parseId(id);
        return VehicleWebMapper.toResponse(changeToMaintenance.execute(VehicleWebMapper.toCommand(vehicleId, request)));
    }

    /**
     * DELETE /vehicles/{id}: baja lógica. Antes consulta a Asignaciones (G2); si no responde, no se borra.
     * Un vehículo ya eliminado responde 200 igual. Un id que no es UUID responde 404.
     *
     * @param id id tal como llegó en la ruta
     * @return 200 con el vehículo en DELETED
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Baja lógica de un vehículo sin asignaciones pendientes ni en curso")
    @ApiResponse(responseCode = "200", description = "Vehículo en DELETED (también si ya estaba eliminado)")
    @ApiResponse(responseCode = "404", description = "VEHICLE_NOT_FOUND: no existe o el id no es UUID",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "VEHICLE_HAS_ACTIVE_ASSIGNMENTS: tiene asignaciones "
            + "PENDING o IN_PROGRESS", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "ASSIGNMENT_SERVICE_UNAVAILABLE o SERVICE_UNAVAILABLE: "
            + "Asignaciones o la base de datos no responden",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public DeletedVehicleResponse delete(@PathVariable String id) {
        return VehicleWebMapper.toDeletedResponse(deleteVehicle.execute(parseId(id)));
    }

    private static UUID parseId(String rawId) {
        try {
            return VehicleWebMapper.parseUuid(rawId);
        } catch (IllegalArgumentException e) {
            throw new InvalidVehicleIdException(rawId);
        }
    }
}