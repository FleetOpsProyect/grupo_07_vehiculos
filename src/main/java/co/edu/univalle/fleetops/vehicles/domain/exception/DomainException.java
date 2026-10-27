package co.edu.univalle.fleetops.vehicles.domain.exception;

/** Base de los errores de negocio. El código viaja tal cual en el campo {@code code} del error. */
public abstract class DomainException extends RuntimeException {

    private final String code;

    /**
     * Crea un error de negocio.
     *
     * @param code código estable del contrato, por ejemplo {@code VEHICLE_NOT_FOUND}
     * @param message texto orientativo
     */
    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Crea un error de negocio causado por otro error.
     *
     * @param code código estable del contrato
     * @param message texto orientativo
     * @param cause error original
     */
    protected DomainException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * Devuelve el código del error.
     *
     * @return el código del contrato
     */
    public String code() {
        return code;
    }
}
