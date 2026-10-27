package co.edu.univalle.fleetops.vehicles.application.port.out;

import java.util.function.Supplier;

/** Ejecuta una acción en una transacción, para que la aplicación no dependa de Spring. */
public interface TransactionPort {

    /**
     * Ejecuta la acción en una transacción: confirma si termina y revierte si lanza una excepción.
     *
     * @param action acción a ejecutar
     * @param <T> tipo del resultado
     * @return lo que devuelve la acción
     */
    <T> T execute(Supplier<T> action);
}
