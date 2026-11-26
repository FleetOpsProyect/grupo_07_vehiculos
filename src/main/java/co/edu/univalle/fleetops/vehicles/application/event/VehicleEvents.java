package co.edu.univalle.fleetops.vehicles.application.event;

import co.edu.univalle.fleetops.vehicles.domain.model.Vehicle;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleBlock;
import co.edu.univalle.fleetops.vehicles.domain.model.VehicleStatus;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.List;
import java.util.Map;

/**
 * Nombres y contenido ({@code data}) de los eventos que publica Vehículos, según la sección 4 del
 * contrato. Las claves ya van en snake_case y los enums como texto, para no depender de cómo se
 * serialice después. Todos llevan {@code version_number}.
 */
public final class VehicleEvents {

    /** Alta de un vehículo. */
    public static final String CREATED = "vehicle_created_event";
    /** Edición de los datos operativos. */
    public static final String UPDATED = "vehicle_updated_event";
    /** Baja lógica. */
    public static final String DELETED = "vehicle_deleted_event";
    /** Cambio de estado o de bloqueos. */
    public static final String STATUS_CHANGED = "vehicle_status_changed_event";
    /** Suma de la distancia de una ruta al odómetro. */
    public static final String ODOMETER_UPDATED = "vehicle_odometer_updated_event";
    /** Acción de {@code cause} cuando se agrega un bloqueo. */
    public static final String BLOCK_ADDED = "BLOCK_ADDED";
    /** Acción de {@code cause} cuando se quita un bloqueo. */
    public static final String BLOCK_REMOVED = "BLOCK_REMOVED";
    /** Acción de {@code cause} cuando empieza una ruta (tipo {@code ROUTE}). */
    public static final String ROUTE_STARTED = "ROUTE_STARTED";
    /** Acción de {@code cause} cuando termina una ruta (tipo {@code ROUTE}). */
    public static final String ROUTE_FINISHED = "ROUTE_FINISHED";

    private static final String VEHICLE_ID = "vehicle_id";
    private static final String PLATE = "plate";
    private static final String STATUS = "status";
    private static final String VERSION_NUMBER = "version_number";

    private VehicleEvents() {
    }

    /**
     * Contenido de {@code vehicle_created_event}: el registro completo en ACTIVE y versión 0.
     *
     * @param vehicle el vehículo recién guardado
     * @return el {@code data} del evento
     */
    public static Map<String, Object> created(Vehicle vehicle) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(VEHICLE_ID, vehicle.id());
        data.put(PLATE, vehicle.plate());
        data.put("vehicle_type", vehicle.type().name());
        putDetails(data, vehicle);
        data.put("odometer_km", vehicle.odometerKm());
        data.put(STATUS, vehicle.status().name());
        data.put(VERSION_NUMBER, vehicle.versionNumber());
        return data;
    }

    /**
     * Contenido de {@code vehicle_updated_event}: el registro completo y los campos que cambiaron.
     *
     * @param vehicle el vehículo ya editado
     * @param changedFields campos que cambiaron, en el orden de {@link Vehicle#update}
     * @return el {@code data} del evento
     */
    public static Map<String, Object> updated(Vehicle vehicle, List<String> changedFields) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(VEHICLE_ID, vehicle.id());
        data.put("changed_fields", List.copyOf(changedFields));
        data.put(PLATE, vehicle.plate());
        data.put("vehicle_type", vehicle.type().name());
        putDetails(data, vehicle);
        data.put(VERSION_NUMBER, vehicle.versionNumber());
        return data;
    }

    /**
     * Contenido de {@code vehicle_deleted_event}. {@code deleted_at} es el momento de la baja.
     *
     * @param vehicle el vehículo ya marcado como eliminado
     * @return el {@code data} del evento
     */
    public static Map<String, Object> deleted(Vehicle vehicle) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(VEHICLE_ID, vehicle.id());
        data.put(PLATE, vehicle.plate());
        data.put("deleted_at", vehicle.updatedAt());
        data.put(VERSION_NUMBER, vehicle.versionNumber());
        return data;
    }

    /**
     * Contenido de {@code vehicle_status_changed_event}: estado anterior y nuevo, la causa, los
     * bloqueos activos y si el vehículo se puede asignar (solo en ACTIVE).
     *
     * @param vehicle el vehículo ya cambiado
     * @param previousStatus estado antes del cambio
     * @param causeType tipo de la causa, por ejemplo {@code MAINTENANCE}
     * @param action acción sobre el bloqueo, por ejemplo {@link #BLOCK_ADDED}
     * @param sourceId id del mantenimiento, incidente o documento que causó el cambio
     * @return el {@code data} del evento
     */
    public static Map<String, Object> statusChanged(Vehicle vehicle, VehicleStatus previousStatus,
                                                    String causeType, String action, String sourceId) {
        Map<String, Object> cause = new LinkedHashMap<>();
        cause.put("type", causeType);
        cause.put("action", action);
        cause.put("source_id", sourceId);
        List<Map<String, Object>> activeBlocks = vehicle.blocks().stream()
                .map(VehicleEvents::block)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(VEHICLE_ID, vehicle.id());
        data.put("previous_status", previousStatus.name());
        data.put(STATUS, vehicle.status().name());
        data.put("cause", cause);
        data.put("active_blocks", activeBlocks);
        data.put("is_assignable", vehicle.status() == VehicleStatus.ACTIVE);
        data.put(VERSION_NUMBER, vehicle.versionNumber());
        return data;
    }

    /**
     * Contenido de {@code vehicle_odometer_updated_event}: odómetro anterior, distancia sumada y nuevo.
     *
     * @param vehicle el vehículo con el odómetro ya sumado
     * @param routeId ruta que se sumó
     * @param previousOdometerKm odómetro antes de sumar
     * @param distanceKm distancia sumada
     * @return el {@code data} del evento
     */
    public static Map<String, Object> odometerUpdated(Vehicle vehicle, UUID routeId, BigDecimal previousOdometerKm,
                                                      BigDecimal distanceKm) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(VEHICLE_ID, vehicle.id());
        data.put("route_id", routeId);
        data.put("previous_odometer_km", previousOdometerKm);
        data.put("distance_km", distanceKm);
        data.put("odometer_km", vehicle.odometerKm());
        data.put(VERSION_NUMBER, vehicle.versionNumber());
        return data;
    }

    private static void putDetails(Map<String, Object> data, Vehicle vehicle) {
        data.put("brand", vehicle.details().brand());
        data.put("model", vehicle.details().model());
        data.put("year", vehicle.details().year());
        data.put("color", vehicle.details().color());
        data.put("notes", vehicle.details().notes());
    }

    private static Map<String, Object> block(VehicleBlock block) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("block_key", block.blockKey());
        data.put("type", block.type().name());
        return data;
    }
}
