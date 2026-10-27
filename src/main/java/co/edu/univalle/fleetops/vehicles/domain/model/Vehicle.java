package co.edu.univalle.fleetops.vehicles.domain.model;

import co.edu.univalle.fleetops.vehicles.domain.exception.InvalidStatusTransitionException;
import co.edu.univalle.fleetops.vehicles.domain.rule.VehicleStatusRule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Vehículo de la flota. El estado nunca se asigna a mano: se recalcula a partir de los bloqueos
 * con {@link VehicleStatusRule}. Sin dependencias de Spring ni de JPA.
 */
public final class Vehicle {

    private static final String PREVENTIVE = "PREVENTIVE";

    private final UUID id;
    private final String plate;
    private final VehicleType type;
    private VehicleDetails details;
    private final BigDecimal odometerKm;
    private VehicleStatus status;
    private final List<VehicleBlock> blocks;
    private final UUID activeRouteId;
    private boolean deleted;
    private final OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private Vehicle(UUID id, String plate, VehicleType type, VehicleDetails details, BigDecimal odometerKm,
                    VehicleStatus status, List<VehicleBlock> blocks, UUID activeRouteId, boolean deleted,
                    OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.plate = plate;
        this.type = type;
        this.details = details;
        this.odometerKm = odometerKm;
        this.status = status;
        this.blocks = new ArrayList<>(blocks);
        this.activeRouteId = activeRouteId;
        this.deleted = deleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Crea un vehículo nuevo en ACTIVE. Los datos ya deben estar validados.
     *
     * @param plate placa normalizada
     * @param type tipo de vehículo
     * @param details datos operativos
     * @param odometerKm odómetro inicial; se guarda con un decimal
     * @param now momento del alta
     * @return el vehículo con id nuevo, sin bloqueos ni ruta
     */
    public static Vehicle register(String plate, VehicleType type, VehicleDetails details,
                                   BigDecimal odometerKm, OffsetDateTime now) {
        return new Vehicle(UUID.randomUUID(), plate, type, details, odometerKm.setScale(1, RoundingMode.HALF_UP),
                VehicleStatus.ACTIVE, List.of(), null, false, now, now);
    }

    /**
     * Reconstruye un vehículo ya guardado, sin recalcular nada.
     *
     * @param id identificador
     * @param plate placa
     * @param type tipo
     * @param details datos operativos
     * @param odometerKm odómetro oficial
     * @param status estado guardado
     * @param blocks bloqueos activos
     * @param activeRouteId ruta en curso, o nulo
     * @param deleted baja lógica
     * @param createdAt fecha de creación
     * @param updatedAt fecha de la última modificación
     * @return el vehículo tal como estaba guardado
     */
    public static Vehicle restore(UUID id, String plate, VehicleType type, VehicleDetails details,
                                  BigDecimal odometerKm, VehicleStatus status, List<VehicleBlock> blocks,
                                  UUID activeRouteId, boolean deleted, OffsetDateTime createdAt,
                                  OffsetDateTime updatedAt) {
        return new Vehicle(id, plate, type, details, odometerKm, status, blocks, activeRouteId, deleted,
                createdAt, updatedAt);
    }

    /**
     * Reemplaza los datos operativos editables.
     *
     * @param newDetails datos ya validados
     * @param now momento de la edición
     */
    public void update(VehicleDetails newDetails, OffsetDateTime now) {
        this.details = newDetails;
        this.updatedAt = now;
    }

    /**
     * Agrega el bloqueo de mantenimiento preventivo. Repetir el mismo {@code maintenanceId} no
     * cambia nada.
     *
     * @param maintenanceId id del mantenimiento
     * @param now momento del cambio
     * @return {@code true} si cambió el vehículo, {@code false} si ya tenía ese bloqueo
     * @throws InvalidStatusTransitionException si el vehículo no estaba ACTIVE
     */
    public boolean startMaintenance(UUID maintenanceId, OffsetDateTime now) {
        VehicleBlock block = VehicleBlock.of(BlockType.MAINTENANCE, maintenanceId.toString(), PREVENTIVE, now);
        if (blocks.stream().anyMatch(existing -> existing.blockKey().equals(block.blockKey()))) {
            return false;
        }
        if (status != VehicleStatus.ACTIVE) {
            throw new InvalidStatusTransitionException(status);
        }
        blocks.add(block);
        status = VehicleStatusRule.calculate(deleted, blocks, activeRouteId);
        updatedAt = now;
        return true;
    }

    /**
     * Marca la baja lógica. El registro nunca se borra físicamente.
     *
     * @param now momento de la baja
     */
    public void markDeleted(OffsetDateTime now) {
        this.deleted = true;
        this.status = VehicleStatusRule.calculate(true, blocks, activeRouteId);
        this.updatedAt = now;
    }

    /**
     * Devuelve el id del vehículo.
     *
     * @return el id
     */
    public UUID id() {
        return id;
    }

    /**
     * Devuelve la placa normalizada.
     *
     * @return la placa
     */
    public String plate() {
        return plate;
    }

    /**
     * Devuelve el tipo de vehículo.
     *
     * @return el tipo
     */
    public VehicleType type() {
        return type;
    }

    /**
     * Devuelve los datos operativos.
     *
     * @return marca, modelo, año, color y observaciones
     */
    public VehicleDetails details() {
        return details;
    }

    /**
     * Devuelve el odómetro oficial.
     *
     * @return kilómetros con un decimal
     */
    public BigDecimal odometerKm() {
        return odometerKm;
    }

    /**
     * Devuelve el estado actual.
     *
     * @return el estado
     */
    public VehicleStatus status() {
        return status;
    }

    /**
     * Devuelve los bloqueos activos.
     *
     * @return una vista de solo lectura
     */
    public List<VehicleBlock> blocks() {
        return Collections.unmodifiableList(blocks);
    }

    /**
     * Devuelve la ruta en curso.
     *
     * @return el id de la ruta, o nulo si no hay
     */
    public UUID activeRouteId() {
        return activeRouteId;
    }

    /**
     * Indica si tiene baja lógica.
     *
     * @return {@code true} si está eliminado
     */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * Devuelve la fecha de creación.
     *
     * @return la fecha de creación
     */
    public OffsetDateTime createdAt() {
        return createdAt;
    }

    /**
     * Devuelve la fecha de la última modificación.
     *
     * @return la fecha de actualización
     */
    public OffsetDateTime updatedAt() {
        return updatedAt;
    }
}
