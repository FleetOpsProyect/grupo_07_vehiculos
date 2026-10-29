package co.edu.univalle.fleetops.vehicles.infrastructure.web.dto;

import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateVehicleStatusRequest(
    @NotNull(message = "El estado es obligatorio")
    VehicleStatus status,

    String reason
) {}
