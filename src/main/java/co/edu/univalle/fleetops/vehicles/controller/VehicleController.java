package co.edu.univalle.fleetops.vehicles.controller;

import co.edu.univalle.fleetops.vehicles.dto.ChangeStatusRequest;
import co.edu.univalle.fleetops.vehicles.dto.CreateVehicleRequest;
import co.edu.univalle.fleetops.vehicles.dto.EligibleVehiclesResponse;
import co.edu.univalle.fleetops.vehicles.dto.PageResponse;
import co.edu.univalle.fleetops.vehicles.dto.UpdateVehicleRequest;
import co.edu.univalle.fleetops.vehicles.dto.VehicleResponse;
import co.edu.univalle.fleetops.vehicles.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.model.VehicleType;
import co.edu.univalle.fleetops.vehicles.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Endpoints del MS Vehículos según el catálogo FleetOps_Endpoints_y_Eventos.
 * El controller solo recibe, delega al service y responde.
 */
@RestController
@RequestMapping("/vehicles")
@Tag(name = "Vehículos", description = "Gestión de la flota — MS Vehículos (Grupo 7)")
public class VehicleController {

    /** Servicio de lógica de negocio. */
    private final VehicleService vehicleService;

    /**
     * Constructor con inyección de dependencias.
     * @param vehicleService servicio de vehículos
     */
    public VehicleController(final VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    /**
     * Registra un vehículo en estado ACTIVE.
     * @param request datos del vehículo
     * @return vehículo creado con HTTP 201 y cabecera Location
     */
    @PostMapping
    @Operation(summary = "Registrar vehículo",
            description = "Crea el vehículo en ACTIVE. Los documentos se registran después en Documentación.")
    public ResponseEntity<VehicleResponse> create(@RequestBody final CreateVehicleRequest request) {
        VehicleResponse created = vehicleService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.vehicleId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Consulta un vehículo por id.
     * @param id id del vehículo
     * @return vehículo con HTTP 200
     */
    @GetMapping("/{id}")
    @Operation(summary = "Consultar vehículo",
            description = "Devuelve estado, bloqueos y kilometraje. No llama a Documentación.")
    public ResponseEntity<VehicleResponse> getById(@PathVariable final UUID id) {
        return ResponseEntity.ok(vehicleService.getById(id));
    }

    /**
     * Lista vehículos paginados con bloque resumen por estado.
     * @param status filtro por estado (opcional)
     * @param type filtro por tipo (opcional)
     * @param page página, desde 0
     * @param size tamaño de página, de 1 a 100
     * @return página de vehículos con HTTP 200
     */
    @GetMapping(params = "!valid_until")
    @Operation(summary = "Listar vehículos",
            description = "Paginado, con conteo por estado para Reportes y Mantenimiento.")
    public ResponseEntity<PageResponse<VehicleResponse>> list(
            @RequestParam(required = false) final VehicleStatus status,
            @RequestParam(required = false) final VehicleType type,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        return ResponseEntity.ok(vehicleService.list(status, type, page, size));
    }

    /**
     * Lista los vehículos aptos para asignar: ACTIVE y con documentos vigentes.
     * @param type filtro por tipo (opcional)
     * @param validUntil fecha (AAAA-MM-DD) hasta la que deben estar vigentes los documentos
     * @return aptos sin paginar (máximo 500) con HTTP 200
     */
    @GetMapping(params = "valid_until")
    @Operation(summary = "Listar vehículos aptos",
            description = "Lo usa Asignaciones. Valida vigencia en lote con Documentación; 503 si no responde.")
    public ResponseEntity<EligibleVehiclesResponse> listEligible(
            @RequestParam(required = false) final VehicleType type,
            @RequestParam(name = "valid_until") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            final LocalDate validUntil) {
        return ResponseEntity.ok(vehicleService.listEligible(type, validUntil));
    }

    /**
     * Pasa el vehículo a IN_MAINTENANCE; lo usa el scheduler de Mantenimiento.
     * @param id id del vehículo
     * @param request estado pedido y orden de mantenimiento
     * @return vehículo actualizado con HTTP 200
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "Pasar a mantenimiento",
            description = "Solo IN_MAINTENANCE y solo desde ACTIVE. Los demás cambios de estado llegan por eventos.")
    public ResponseEntity<VehicleResponse> changeStatus(
            @PathVariable final UUID id,
            @RequestBody final ChangeStatusRequest request) {
        return ResponseEntity.ok(vehicleService.changeToMaintenance(id, request));
    }

    /**
     * Modifica los datos editables del vehículo.
     * @param id id del vehículo
     * @param request datos nuevos
     * @return vehículo actualizado con HTTP 200
     */
    @PutMapping("/{id}")
    @Operation(summary = "Modificar vehículo",
            description = "Reemplaza marca, modelo, año, color y observaciones. Placa, tipo y kilometraje no cambian.")
    public ResponseEntity<VehicleResponse> update(
            @PathVariable final UUID id,
            @RequestBody final UpdateVehicleRequest request) {
        return ResponseEntity.ok(vehicleService.update(id, request));
    }

    /**
     * Baja lógica del vehículo.
     * @param id id del vehículo
     * @return vehículo eliminado con HTTP 200
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar vehículo",
            description = "Baja lógica. 409 con asignaciones pendientes o en curso; 503 si Asignaciones no responde.")
    public ResponseEntity<VehicleResponse> delete(@PathVariable final UUID id) {
        return ResponseEntity.ok(vehicleService.delete(id));
    }
}
