package co.edu.univalle.fleetops.vehicles.infrastructure.persistence.entity;

import co.edu.univalle.fleetops.vehicles.domain.model.StatusChangeCause;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Fila de la tabla {@code status_history}. */
@Entity
@Table(name = "status_history")
@Getter
@Setter
@NoArgsConstructor
public class StatusHistoryEntity {

    @Id
    @Column(name = "history_id")
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private VehicleStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private VehicleStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "cause", nullable = false, length = 60)
    private StatusChangeCause cause;

    @Column(name = "source_id", length = 64)
    private String sourceId;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;
}
