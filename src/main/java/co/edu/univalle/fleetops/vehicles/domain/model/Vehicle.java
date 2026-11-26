package co.edu.univalle.fleetops.vehicles.domain.model;

import co.edu.univalle.fleetops.vehicles.domain.exception.InvalidStatusTransitionException;
import co.edu.univalle.fleetops.vehicles.domain.rule.VehicleStatusRule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Vehículo de la flota. El estado nunca se asigna a mano: se recalcula a partir de los bloqueos
 * con {@link VehicleStatusRule}. {@code versionNumber} es la versión de negocio que viaja en los eventos:
 * 0 en el alta y +1 con cada cambio. Sin dependencias de Spring ni de JPA.
 */
public final class Vehicle {

    private static final String PREVENTIVE = "PREVENTIVE";

    private final UUID id;
    private final String plate;
    private final VehicleType type;
    private VehicleDetails details;
    private BigDecimal odometerKm;
    private VehicleStatus status;
    private final List<VehicleBlock> blocks;
    private UUID activeRouteId;
    private boolean deleted;
    private long versionNumber;
    private final OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private Vehicle(UUID id, String plate, VehicleType type, VehicleDetails details, BigDecimal odometerKm,
                    VehicleStatus status, List<VehicleBlock> blocks, UUID activeRouteId, boolean deleted,
                    long versionNumber, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.plate = plate;
        this.type = type;
        this.details = details;
        this.odometerKm = odometerKm;
        this.status = status;
        this.blocks = new ArrayList<>(blocks);
        this.activeRouteId = activeRouteId;
        this.deleted = deleted;
        this.versionNumber = versionNumber;
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
     * @return el vehículo con id nuevo, sin bloqueos ni ruta y en la versión 0
     */
    public static Vehicle register(String plate, VehicleType type, VehicleDetails details,
                                   BigDecimal odometerKm, OffsetDateTime now) {
        return new Vehicle(UUID.randomUUID(), plate, type, details, odometerKm.setScale(1, RoundingMode.HALF_UP),
                VehicleStatus.ACTIVE, List.of(), null, false, 0, now, now);
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
     * @param versionNumber versión de negocio guardada
     * @param createdAt fecha de creación
     * @param updatedAt fecha de la última modificación
     * @return el vehículo tal como estaba guardado
     */
    public static Vehicle restore(UUID id, String plate, VehicleType type, VehicleDetails details,
                                  BigDecimal odometerKm, VehicleStatus status, List<VehicleBlock> blocks,
                                  UUID activeRouteId, boolean deleted, long versionNumber,
                                  OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        return new Vehicle(id, plate, type, details, odometerKm, status, blocks, activeRouteId, deleted,
                versionNumber, createdAt, updatedAt);
    }

    /**
     * Reemplaza los datos operativos editables. Si ningún campo cambia, el vehículo queda igual
     * (ni versión ni fecha de modificación).
     *
     * @param newDetails datos ya validados
     * @param now momento de la edición
     * @return los campos que cambiaron ({@code brand}, {@code model}, {@code year}, {@code color},
     *     {@code notes}), en ese orden; vacío si no cambió ninguno
     */
    public List<String> update(VehicleDetails newDetails, OffsetDateTime now) {
        List<String> changed = new ArrayList<>();
        addIfChanged(changed, "brand", details.brand(), newDetails.brand());
        addIfChanged(changed, "model", details.model(), newDetails.model());
        addIfChanged(changed, "year", details.year(), newDetails.year());
        addIfChanged(changed, "color", details.color(), newDetails.color());
        addIfChanged(changed, "notes", details.notes(), newDetails.notes());
        if (!changed.isEmpty()) {
            this.details = newDetails;
            this.versionNumber++;
            this.updatedAt = now;
        }
        return List.copyOf(changed);
    }

    private static void addIfChanged(List<String> changed, String field, Object current, Object next) {
        if (!Objects.equals(current, next)) {
            changed.add(field);
        }
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
        recalculate(now);
        return true;
    }

    /**
     * Agrega un bloqueo que llega por un evento (incidente grave o documento vencido), sin importar el
     * estado actual: el estado se recalcula. Un bloqueo con la misma clave no se repite.
     *
     * @param type tipo de bloqueo
     * @param sourceId id del incidente o del documento
     * @param reason detalle, por ejemplo el tipo de documento
     * @param now momento del cambio
     * @return {@code true} si se agregó, {@code false} si ya existía
     */
    public boolean addBlock(BlockType type, String sourceId, String reason, OffsetDateTime now) {
        VehicleBlock block = VehicleBlock.of(type, sourceId, reason, now);
        if (blocks.stream().anyMatch(existing -> existing.blockKey().equals(block.blockKey()))) {
            return false;
        }
        blocks.add(block);
        recalculate(now);
        return true;
    }

    /**
     * Quita los bloqueos con esas claves; las que no existen se ignoran. El vehículo vuelve a ACTIVE solo
     * si no le queda ningún bloqueo ni ruta en curso.
     *
     * @param blockKeys claves, por ejemplo {@code MAINTENANCE:<id>}
     * @param now momento del cambio
     * @return {@code true} si quitó alguno
     */
    public boolean removeBlocks(List<String> blockKeys, OffsetDateTime now) {
        if (!blocks.removeIf(block -> blockKeys.contains(block.blockKey()))) {
            return false;
        }
        recalculate(now);
        return true;
    }

    /**
     * Marca la ruta en curso. Pasa a ON_ROUTE solo si no tiene bloqueos.
     *
     * @param routeId ruta iniciada
     * @param now momento del cambio
     * @return {@code true} si cambió, {@code false} si ya tenía esa ruta
     */
    public boolean startRoute(UUID routeId, OffsetDateTime now) {
        if (routeId.equals(activeRouteId)) {
            return false;
        }
        activeRouteId = routeId;
        recalculate(now);
        return true;
    }

    /**
     * Suma la distancia de una ruta al odómetro y, si era la ruta en curso, la libera. Quien llama
     * garantiza que cada ruta se sume una sola vez. Aplica también a un vehículo eliminado.
     *
     * @param routeId ruta terminada
     * @param distanceKm kilómetros recorridos, no negativos
     * @param now momento del cambio
     */
    public void finishRoute(UUID routeId, BigDecimal distanceKm, OffsetDateTime now) {
        odometerKm = odometerKm.add(distanceKm).setScale(1, RoundingMode.HALF_UP);
        if (routeId.equals(activeRouteId)) {
            activeRouteId = null;
        }
        recalculate(now);
    }

    private void recalculate(OffsetDateTime now) {
        status = VehicleStatusRule.calculate(deleted, blocks, activeRouteId);
        versionNumber++;
        updatedAt = now;
    }

    /**
     * Marca la baja lógica. El registro nunca se borra físicamente.
     *
     * @param now momento de la baja
     */
    public void markDeleted(OffsetDateTime now) {
        this.deleted = true;
        recalculate(now);
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
     * Devuelve la versión de negocio.
     *
     * @return 0 en el alta; sube 1 con cada cambio
     */
    public long versionNumber() {
        return versionNumber;
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
