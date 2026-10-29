package co.edu.univalle.fleetops.vehicles.infrastructure.web.dto;

import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.time.LocalDate;
import java.util.UUID;

public record VehicleSummaryResponse(
    UUID id,
    String plate,
    String type,
    VehicleStatus status,
    LocalDate validUntil
) {}
