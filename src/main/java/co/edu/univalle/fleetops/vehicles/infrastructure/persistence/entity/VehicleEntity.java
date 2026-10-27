package co.edu.univalle.fleetops.vehicles.infrastructure.persistence.entity;

import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/** Fila de la tabla {@code vehicle}. {@code version} es el bloqueo optimista de JPA. */
@Entity
@Table(name = "vehicle")
@Getter
@Setter
@NoArgsConstructor
public class VehicleEntity {

    @Id
    @Column(name = "vehicle_id")
    private UUID id;

    @Column(name = "plate", nullable = false, length = 6)
    private String plate;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 30)
    private VehicleType vehicleType;

    @Column(name = "brand", nullable = false, length = 50)
    private String brand;

    @Column(name = "model", nullable = false, length = 50)
    private String model;

    @Column(name = "vehicle_year", nullable = false)
    private Integer year;

    @Column(name = "color", length = 30)
    private String color;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "odometer_km", nullable = false, precision = 10, scale = 1)
    private BigDecimal odometerKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private VehicleStatus status;

    @Column(name = "active_route_id")
    private UUID activeRouteId;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, blockKey ASC")
    @BatchSize(size = 100)
    private List<VehicleBlockEntity> blocks = new ArrayList<>();
}
