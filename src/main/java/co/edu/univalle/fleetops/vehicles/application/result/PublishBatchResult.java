package co.edu.univalle.fleetops.vehicles.application.result;

/**
 * Resultado de una vuelta del publicador.
 *
 * @param published eventos publicados y marcados como PUBLISHED
 * @param failed eventos que fallaron y siguen PENDING
 */
public record PublishBatchResult(int published, int failed) {
}
