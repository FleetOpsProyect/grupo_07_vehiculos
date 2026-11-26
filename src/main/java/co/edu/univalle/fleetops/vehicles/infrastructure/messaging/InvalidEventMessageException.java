package co.edu.univalle.fleetops.vehicles.infrastructure.messaging;

/** El cuerpo de un mensaje de la cola no es un sobre válido; no se borra y termina en la DLQ. */
public class InvalidEventMessageException extends RuntimeException {

    /**
     * Crea el error.
     *
     * @param message qué le falta o qué tiene mal el mensaje
     */
    public InvalidEventMessageException(String message) {
        super(message);
    }

    /**
     * Crea el error con la causa original.
     *
     * @param message qué tiene mal el mensaje
     * @param cause error de lectura del JSON
     */
    public InvalidEventMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
