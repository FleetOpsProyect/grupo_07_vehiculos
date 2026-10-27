package co.edu.univalle.fleetops.vehicles.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Consulta a Documentación (G1) de qué vehículos tienen vigentes los documentos obligatorios. */
public interface DocumentValidityPort {

    /**
     * Devuelve los vehículos con SOAT, revisión técnico-mecánica y tarjeta de propiedad vigentes.
     *
     * @param vehicleIds hasta 100 ids por llamada
     * @param validUntil fecha hasta la que deben estar vigentes
     * @return los ids aptos
     * @throws co.edu.univalle.fleetops.vehicles.application.exception.DocumentServiceUnavailableException
     *     si Documentación no responde en 2 s
     */
    Set<UUID> findVehiclesWithValidDocuments(List<UUID> vehicleIds, LocalDate validUntil);
}
