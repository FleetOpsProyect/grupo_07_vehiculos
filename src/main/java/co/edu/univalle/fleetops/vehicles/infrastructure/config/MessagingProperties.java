package co.edu.univalle.fleetops.vehicles.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración de la mensajería ({@code fleetops.messaging.*}).
 *
 * @param enabled apaga el publicador y el listener (por ejemplo en pruebas sin AWS)
 * @param region región de AWS
 * @param endpoint URL alternativa del broker; en local es LocalStack, en AWS va vacía
 * @param topicArn ARN del topic donde se publica ({@code fleetops_vehicle_events}); vacío = no publica
 * @param queueUrl URL de la cola que se consume ({@code vehicles_queue}); vacía = no consume
 * @param publisher ajustes del publicador del outbox
 * @param listener ajustes del listener de la cola
 */
@ConfigurationProperties(prefix = "fleetops.messaging")
public record MessagingProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("us-east-1") String region,
        @DefaultValue("") String endpoint,
        @DefaultValue("") String topicArn,
        @DefaultValue("") String queueUrl,
        @DefaultValue Publisher publisher,
        @DefaultValue Listener listener) {

    /**
     * Ajustes del publicador.
     *
     * @param batchSize eventos que toma por vuelta
     * @param fixedDelayMs espera entre vueltas, en milisegundos
     * @param timeoutMs tiempo máximo de una llamada a SNS, en milisegundos
     */
    public record Publisher(@DefaultValue("50") int batchSize, @DefaultValue("2000") long fixedDelayMs,
                            @DefaultValue("2000") long timeoutMs) {
    }

    /**
     * Ajustes del listener.
     *
     * @param waitTimeSeconds espera máxima de cada lectura de la cola (long polling)
     * @param maxMessages mensajes que lee por vuelta (1 a 10)
     * @param errorBackoffMs pausa tras un error al leer la cola, en milisegundos
     */
    public record Listener(@DefaultValue("20") int waitTimeSeconds, @DefaultValue("10") int maxMessages,
                           @DefaultValue("5000") long errorBackoffMs) {
    }

    /**
     * Indica si hay un topic configurado y la mensajería está activa.
     *
     * @return {@code true} si el publicador debe arrancar
     */
    public boolean publishingEnabled() {
        return enabled && !topicArn.isBlank();
    }

    /**
     * Indica si hay una cola configurada y la mensajería está activa.
     *
     * @return {@code true} si el listener debe arrancar
     */
    public boolean listeningEnabled() {
        return enabled && !queueUrl.isBlank();
    }
}
