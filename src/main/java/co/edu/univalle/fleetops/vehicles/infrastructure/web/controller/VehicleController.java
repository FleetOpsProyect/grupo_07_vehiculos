package co.edu.univalle.fleetops.vehicles.infrastructure.web.controller;

import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.ChangeStatusRequest;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.CreateVehicleRequest;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.dto.VehicleResponse;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.error.InvalidVehicleIdException;
import co.edu.univalle.fleetops.vehicles.infrastructure.web.mapper.VehicleWebMapper;
import co.edu.univalle.fleetops.vehicles.application.port.in.ChangeToMaintenanceUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.GetVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.RegisterVehicleUseCase;
import co.edu.univalle.fleetops.vehicles.application.result.VehicleResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    private final ChangeToMaintenanceUseCase changeToMaintenance;

    /**
     * Crea el controller con los puertos de entrada que usa.
     *
     * @param registerVehicle caso de uso de alta
     * @param getVehicle caso de uso de detalle
     * @param changeToMaintenance caso de uso de paso a mantenimiento
     */
    public VehicleController(RegisterVehicleUseCase registerVehicle, GetVehicleUseCase getVehicle,
            ChangeToMaintenanceUseCase changeToMaintenance) {
        this.registerVehicle = registerVehicle;
        this.getVehicle = getVehicle;
        this.changeToMaintenance = changeToMaintenance;
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
     * GET /vehicles/{id}: detalle de un vehículo. Un id que no es UUID responde 404.
     *
     * @param id id tal como llegó en la ruta
     * @return 200 con el vehículo
     */
    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un vehículo (estado, odómetro oficial, bloqueos)")
    @ApiResponse(responseCode = "200", description = "Vehículo encontrado")
    @ApiResponse(responseCode = "404", description = "VEHICLE_NOT_FOUND")
    public VehicleResponse get(@PathVariable String id) {
        return VehicleWebMapper.toResponse(getVehicle.execute(parseId(id)));
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

    private static UUID parseId(String rawId) {
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            throw new InvalidVehicleIdException(rawId);
        }
    }
}
