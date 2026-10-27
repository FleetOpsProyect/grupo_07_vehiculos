package co.edu.univalle.fleetops.vehicles.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Entidad JPA de un cambio de estado del vehículo (trazabilidad). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "status_history")
public class StatusHistoryModel {

    /** Identificador del registro. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "history_id")
    private UUID id;

    /** Vehículo al que pertenece el cambio. */
    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    /** Estado anterior; null en el registro inicial. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private VehicleStatus fromStatus;

    /** Estado nuevo. */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private VehicleStatus toStatus;

    /** Causa del cambio, ej. INCIDENT_REPORTED. */
    @Column(name = "cause", nullable = false, length = 60)
    private String cause;

    /** Id del recurso o evento que causó el cambio. */
    @Column(name = "source_id", length = 64)
    private String sourceId;

    /** Fecha del cambio. */
    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;
}
