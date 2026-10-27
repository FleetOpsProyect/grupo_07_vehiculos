package co.edu.univalle.fleetops.vehicles.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad JPA que representa un vehículo de la flota.
 * El estado no se edita a mano: se deriva de los bloqueos activos y de la ruta en curso.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "vehicle")
public class VehicleModel {

    /** Identificador UUID generado por el servicio. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "vehicle_id")
    private UUID id;

    /** Placa normalizada (ej. ABC123), única entre vehículos no eliminados. */
    @Column(name = "plate", nullable = false, length = 6)
    private String plate;

    /** Tipo de vehículo; no se puede cambiar después del registro. */
    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 30)
    private VehicleType vehicleType;

    /** Marca. */
    @Column(name = "brand", nullable = false, length = 50)
    private String brand;

    /** Modelo o línea. */
    @Column(name = "model", nullable = false, length = 50)
    private String model;

    /** Año del modelo. */
    @Column(name = "vehicle_year", nullable = false)
    private Integer year;

    /** Color. */
    @Column(name = "color", length = 30)
    private String color;

    /** Observaciones. */
    @Column(name = "notes", length = 500)
    private String notes;

    /** Kilometraje oficial; solo crece con las rutas finalizadas. */
    @Column(name = "odometer_km", nullable = false, precision = 10, scale = 1)
    private BigDecimal odometerKm;

    /** Estado derivado. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private VehicleStatus status;

    /** Ruta en curso, si la hay. */
    @Column(name = "active_route_id")
    private UUID activeRouteId;

    /** Marca de baja lógica. */
    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    /** Versión para bloqueo optimista. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Fecha de registro. */
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** Fecha de la última modificación. */
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Bloqueos activos.
     * OneToMany: un vehículo tiene varios bloqueos y cada bloqueo pertenece a un vehículo.
     * orphanRemoval borra de la BD el bloqueo que se quita de la colección.
     */
    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<VehicleBlockModel> blocks = new HashSet<>();

    /**
     * Indica si el vehículo tiene un bloqueo con la clave dada.
     * @param blockKey clave del bloqueo, ej. INCIDENT:id
     * @return true si existe
     */
    public boolean hasBlock(final String blockKey) {
        return blocks.stream().anyMatch(block -> block.getBlockKey().equals(blockKey));
    }

    /**
     * Indica si el vehículo tiene algún bloqueo del tipo dado.
     * @param blockType tipo de bloqueo
     * @return true si existe al menos uno
     */
    public boolean hasBlockOfType(final BlockType blockType) {
        return blocks.stream().anyMatch(block -> block.getBlockType() == blockType);
    }

    /**
     * Agrega un bloqueo y enlaza ambos lados de la relación.
     * @param block bloqueo a agregar
     */
    public void addBlock(final VehicleBlockModel block) {
        block.setVehicle(this);
        blocks.add(block);
    }

    /**
     * Quita el bloqueo con la clave dada.
     * @param blockKey clave del bloqueo
     * @return true si había un bloqueo con esa clave
     */
    public boolean removeBlock(final String blockKey) {
        return blocks.removeIf(block -> block.getBlockKey().equals(blockKey));
    }
}
