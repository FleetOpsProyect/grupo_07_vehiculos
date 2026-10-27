package co.edu.univalle.fleetops.vehicles.infrastructure.persistence.entity;

import co.edu.univalle.fleetops.vehicles.domain.model.BlockType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Fila de la tabla {@code vehicle_block}: un bloqueo activo de un vehículo. */
@Entity
@Table(name = "vehicle_block")
@Getter
@Setter
@NoArgsConstructor
public class VehicleBlockEntity {

    @Id
    @Column(name = "block_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private VehicleEntity vehicle;

    @Column(name = "block_key", nullable = false, length = 80)
    private String blockKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", nullable = false, length = 20)
    private BlockType blockType;

    @Column(name = "source_id", nullable = false, length = 64)
    private String sourceId;

    @Column(name = "reason", length = 100)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
