package co.edu.univalle.fleetops.vehicles.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Entidad JPA de una ruta cuyo kilometraje ya se sumó al odómetro. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "applied_route")
public class AppliedRouteModel {

    /** Id de la ruta en Telemetría (G6); asignado, no generado. */
    @Id
    @Column(name = "route_id")
    private UUID routeId;

    /** Vehículo de la ruta. */
    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    /** Distancia sumada al odómetro. */
    @Column(name = "distance_km", nullable = false, precision = 8, scale = 1)
    private BigDecimal distanceKm;

    /** Fecha en que se aplicó. */
    @Column(name = "applied_at", nullable = false)
    private OffsetDateTime appliedAt;
}
