package co.edu.univalle.fleetops.vehicles.application.exception;

/**
 * Un evento recibido no trae un campo obligatorio o lo trae con un formato inválido. El mensaje no se
 * borra de la cola: SQS lo reintenta y al tercer fallo lo pasa a la DLQ para revisarlo.
 */
public class InvalidEventDataException extends RuntimeException {

    /**
     * Crea el error con el campo y el problema.
     *
     * @param field campo de {@code data}
     * @param problem qué tiene mal
     */
    public InvalidEventDataException(String field, String problem) {
        super("Invalid event data: " + field + " " + problem);
    }
}
