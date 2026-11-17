package co.edu.univalle.fleetops.vehicles.application.exception;

/**
 * Otra petición modificó el mismo vehículo mientras esta se procesaba. El caso de uso que la recibe
 * puede reintentar una vez en una transacción nueva; si no lo hace, llega a la capa REST como 503.
 */
public class ConcurrentVehicleUpdateException extends RuntimeException {

    /**
     * Crea el error con la falla original de la persistencia.
     *
     * @param cause conflicto detectado al guardar (versión desactualizada o bloqueo duplicado)
     */
    public ConcurrentVehicleUpdateException(Throwable cause) {
        super("The vehicle was modified by another request", cause);
    }
}
