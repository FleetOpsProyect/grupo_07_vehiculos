package co.edu.univalle.fleetops.vehicles.infrastructure.config;

import co.edu.univalle.fleetops.vehicles.application.port.in.ProcessIncomingEventUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.in.PublishPendingEventsUseCase;
import co.edu.univalle.fleetops.vehicles.application.port.out.EventPublisher;
import co.edu.univalle.fleetops.vehicles.application.port.out.OutboxQueue;
import co.edu.univalle.fleetops.vehicles.application.port.out.TransactionPort;
import co.edu.univalle.fleetops.vehicles.application.usecase.PublishPendingEventsService;
import co.edu.univalle.fleetops.vehicles.infrastructure.messaging.EventJsonMapper;
import co.edu.univalle.fleetops.vehicles.infrastructure.messaging.MessagingMetrics;
import co.edu.univalle.fleetops.vehicles.infrastructure.messaging.OutboxPublisherJob;
import co.edu.univalle.fleetops.vehicles.infrastructure.messaging.SnsEventPublisher;
import co.edu.univalle.fleetops.vehicles.infrastructure.messaging.SqsQueueListener;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.client.builder.SdkClientBuilder;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Conecta la mensajería: clientes de SNS y SQS, publicador del outbox y listener de la cola. Cada pieza
 * arranca solo si {@code fleetops.messaging.enabled} es verdadero y su recurso (topic o cola) está
 * configurado, así un despliegue sin variables de AWS no falla: simplemente no publica ni consume.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(MessagingProperties.class)
public class AwsMessagingConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AwsMessagingConfig.class);
    private static final String PUBLISHING = "${fleetops.messaging.enabled:true} "
            + "&& !'${fleetops.messaging.topic-arn:}'.isEmpty()";
    private static final String LISTENING = "${fleetops.messaging.enabled:true} "
            + "&& !'${fleetops.messaging.queue-url:}'.isEmpty()";

    /**
     * Avisa en el log qué parte de la mensajería quedó activa.
     *
     * @param properties configuración de la mensajería
     * @return el runner que escribe el aviso al arrancar
     */
    @Bean
    public ApplicationRunner messagingStatusLogger(MessagingProperties properties) {
        return args -> LOG.info("Messaging: enabled={}, publishing={}, listening={}", properties.enabled(),
                properties.publishingEnabled(), properties.listeningEnabled());
    }

    /**
     * Cliente de SNS con tiempos cortos y sin reintentos propios: el reintento es del outbox.
     *
     * @param properties configuración de la mensajería
     * @return el cliente
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnExpression(PUBLISHING)
    public SnsClient snsClient(MessagingProperties properties) {
        var builder = SnsClient.builder().region(Region.of(properties.region()))
                .credentialsProvider(credentials())
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofMillis(properties.publisher().timeoutMs()))
                        .socketTimeout(Duration.ofMillis(properties.publisher().timeoutMs())))
                .overrideConfiguration(config -> config.retryStrategy(AwsRetryStrategy.doNotRetry())
                        .apiCallTimeout(Duration.ofMillis(properties.publisher().timeoutMs() * 2)));
        override(builder, properties);
        return builder.build();
    }

    /**
     * Cliente de SQS; el tiempo de lectura es mayor que la espera del long polling.
     *
     * @param properties configuración de la mensajería
     * @return el cliente
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnExpression(LISTENING)
    public SqsClient sqsClient(MessagingProperties properties) {
        var builder = SqsClient.builder().region(Region.of(properties.region()))
                .credentialsProvider(credentials())
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(2))
                        .socketTimeout(Duration.ofSeconds(properties.listener().waitTimeSeconds() + 10L)));
        override(builder, properties);
        return builder.build();
    }

    /**
     * Publicador de eventos en SNS.
     *
     * @param sns cliente de SNS
     * @param properties configuración de la mensajería
     * @return el publicador
     */
    @Bean
    @ConditionalOnExpression(PUBLISHING)
    public EventPublisher eventPublisher(SnsClient sns, MessagingProperties properties) {
        return new SnsEventPublisher(sns, properties.topicArn());
    }

    /**
     * Caso de uso que publica un lote del outbox.
     *
     * @param queue eventos pendientes del outbox
     * @param publisher publicador en SNS
     * @param transaction ejecución transaccional
     * @param clock reloj en GMT-5
     * @return el caso de uso
     */
    @Bean
    @ConditionalOnExpression(PUBLISHING)
    public PublishPendingEventsUseCase publishPendingEventsUseCase(OutboxQueue queue, EventPublisher publisher,
            TransactionPort transaction, Clock clock) {
        return new PublishPendingEventsService(queue, publisher, transaction, clock);
    }

    /**
     * Job que publica el outbox cada 2 s.
     *
     * @param useCase caso de uso que publica un lote
     * @param metrics métricas de la mensajería
     * @param properties configuración de la mensajería
     * @return el job
     */
    @Bean
    @ConditionalOnExpression(PUBLISHING)
    public OutboxPublisherJob outboxPublisherJob(PublishPendingEventsUseCase useCase, MessagingMetrics metrics,
                                                 MessagingProperties properties) {
        return new OutboxPublisherJob(useCase, metrics, properties.publisher().batchSize());
    }

    /**
     * Listener de {@code vehicles_queue}.
     *
     * @param sqs cliente de SQS
     * @param useCase caso de uso que aplica cada mensaje
     * @param json lector del sobre
     * @param metrics métricas de la mensajería
     * @param properties configuración de la mensajería
     * @return el listener
     */
    @Bean
    @ConditionalOnExpression(LISTENING)
    public SqsQueueListener sqsQueueListener(SqsClient sqs, ProcessIncomingEventUseCase useCase,
                                             EventJsonMapper json, MessagingMetrics metrics,
                                             MessagingProperties properties) {
        return new SqsQueueListener(sqs, properties.queueUrl(), useCase, json, metrics,
                properties.listener().waitTimeSeconds(), properties.listener().maxMessages(),
                properties.listener().errorBackoffMs());
    }

    private static void override(SdkClientBuilder<?, ?> builder, MessagingProperties properties) {
        if (!properties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
    }

    /**
     * Cadena de credenciales por defecto del SDK: en el EC2 toma el rol de la instancia; en local (LocalStack)
     * toma {@code AWS_ACCESS_KEY_ID} y {@code AWS_SECRET_ACCESS_KEY} del entorno. El código no lleva claves.
     */
    private static AwsCredentialsProvider credentials() {
        return DefaultCredentialsProvider.builder().build();
    }
}
