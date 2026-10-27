package co.edu.univalle.fleetops.vehicles.application.result;

import java.util.List;
import java.util.function.Function;

/**
 * Página de resultados de un puerto de salida.
 *
 * @param content elementos de la página
 * @param page número de página desde 0
 * @param size tamaño de página pedido
 * @param total total de elementos que cumplen el filtro
 * @param <T> tipo de los elementos
 */
public record PageResult<T>(List<T> content, int page, int size, long total) {

    /**
     * Convierte cada elemento conservando la paginación.
     *
     * @param mapper conversión de un elemento
     * @param <R> tipo de los elementos convertidos
     * @return la misma página con los elementos convertidos
     */
    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(content.stream().map(mapper).toList(), page, size, total);
    }
}
