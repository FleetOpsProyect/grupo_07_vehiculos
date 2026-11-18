package co.edu.univalle.fleetops.vehicles.infrastructure.web.dto;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cuerpo de PUT /vehicles/{id} (snake_case por la configuración global de Jackson).
 *
 * @param brand marca
 * @param model modelo
 * @param year año de fabricación
 * @param color color opcional
 * @param notes observaciones opcionales
 * @param extraFields campos adicionales recibidos en el JSON para detectar atributos no editables
 */
public record UpdateVehicleRequest(
        String brand,
        String model,
        Integer year,
        String color,
        String notes,
        Map<String, Object> extraFields) {


    /**
     * Crea el DTO de edición a partir del JSON recibido.
     *
     * @param brand marca del vehículo
     * @param model modelo del vehículo
     * @param year año del modelo
     * @param color color opcional
     * @param notes observaciones opcionales
     */
    @JsonCreator
    public UpdateVehicleRequest(
            @JsonProperty("brand") String brand,
            @JsonProperty("model") String model,
            @JsonProperty("year") Integer year,
            @JsonProperty("color") String color,
            @JsonProperty("notes") String notes) {
        this(brand, model, year, color, notes, new LinkedHashMap<>());
    }

    /**
     * Registra cualquier campo adicional enviado en el JSON para validar si no es editable.
     *
     * @param key nombre del campo en el JSON
     * @param value valor recibido
     */
    @JsonAnySetter
    public void addExtraField(String key, Object value) {
        extraFields.put(key, value);
    }
}
