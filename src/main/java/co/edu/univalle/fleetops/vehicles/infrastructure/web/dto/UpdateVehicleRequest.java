package co.edu.univalle.fleetops.vehicles.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record UpdateVehicleRequest(
    @NotBlank(message = "La placa es obligatoria")
    String plate,

    @NotBlank(message = "El tipo es obligatorio")
    String type,

    @NotNull(message = "La fecha de vigencia es obligatoria")
    LocalDate validUntil
) {}
