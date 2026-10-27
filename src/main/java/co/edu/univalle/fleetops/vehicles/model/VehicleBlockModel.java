package co.edu.univalle.fleetops.vehicles.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Entidad JPA de un bloqueo activo de un vehículo (ADR-09). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "vehicle_block")
public class VehicleBlockModel {

    /** Identificador interno del bloqueo. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "block_id")
    private UUID id;

    /**
     * Vehículo bloqueado.
     * ManyToOne: muchos bloqueos pueden pertenecer al mismo vehículo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private VehicleModel vehicle;

    /** Clave determinista: INCIDENT:id, MAINTENANCE:id o DOCUMENT:tipo. */
    @Column(name = "block_key", nullable = false, length = 80)
    private String blockKey;

    /** Tipo de bloqueo. */
    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", nullable = false, length = 20)
    private BlockType blockType;

    /** Id del incidente, de la orden de mantenimiento o del documento que lo originó. */
    @Column(name = "source_id", nullable = false, length = 64)
    private String sourceId;

    /** Motivo legible del bloqueo. */
    @Column(name = "reason", length = 100)
    private String reason;

    /** Fecha en que se creó el bloqueo. */
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
