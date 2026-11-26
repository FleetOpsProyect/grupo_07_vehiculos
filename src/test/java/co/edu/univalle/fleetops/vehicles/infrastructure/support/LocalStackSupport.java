package co.edu.univalle.fleetops.vehicles.infrastructure.support;

import java.net.URI;
import java.util.Map;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/**
 * Un LocalStack compartido por todas las pruebas de mensajería, con los mismos recursos que crea
 * {@code localstack/init/ready.d/01-create-resources.sh}: el topic {@code fleetops_vehicle_events}, la
 * cola {@code vehicles_queue} con su DLQ (3 intentos) y una cola de prueba suscrita al topic con
 * entrega sin envoltorio, para ver lo que publica el outbox.
 */
public final class LocalStackSupport {

    public static final String TOPIC_NAME = "fleetops_vehicle_events";
    public static final String QUEUE_NAME = "vehicles_queue";
    public static final String DLQ_NAME = "vehicles_queue_dlq";
    public static final String PROBE_NAME = "vehicle_events_probe";

    private static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4.14.0"))
            .withServices(LocalStackContainer.Service.SNS, LocalStackContainer.Service.SQS);

    private static final SnsClient SNS;
    private static final SqsClient SQS;
    private static final String TOPIC_ARN;
    private static final String QUEUE_URL;
    private static final String DLQ_URL;
    private static final String PROBE_URL;

    static {
        LOCALSTACK.start();
        // Igual que en producción, las credenciales salen de la cadena por defecto del SDK; aquí, de las
        // propiedades del sistema (LocalStack acepta cualquier valor). Las usan también los clientes de la API.
        System.setProperty("aws.accessKeyId", LOCALSTACK.getAccessKey());
        System.setProperty("aws.secretAccessKey", LOCALSTACK.getSecretKey());
        var credentials = DefaultCredentialsProvider.builder().build();
        URI endpoint = LOCALSTACK.getEndpoint();
        SNS = SnsClient.builder().endpointOverride(endpoint).region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(credentials).httpClientBuilder(UrlConnectionHttpClient.builder()).build();
        SQS = SqsClient.builder().endpointOverride(endpoint).region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(credentials).httpClientBuilder(UrlConnectionHttpClient.builder()).build();

        DLQ_URL = SQS.createQueue(request -> request.queueName(DLQ_NAME)).queueUrl();
        String dlqArn = queueArn(DLQ_URL);
        QUEUE_URL = SQS.createQueue(request -> request.queueName(QUEUE_NAME).attributes(Map.of(
                QueueAttributeName.VISIBILITY_TIMEOUT, "2",
                QueueAttributeName.REDRIVE_POLICY,
                "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"3\"}"))).queueUrl();
        PROBE_URL = SQS.createQueue(request -> request.queueName(PROBE_NAME)).queueUrl();
        TOPIC_ARN = createTopicAndProbeSubscription();
    }

    private LocalStackSupport() {
    }

    /** @return URL base de LocalStack */
    public static String endpoint() {
        return LOCALSTACK.getEndpoint().toString();
    }

    /** @return región configurada en el contenedor */
    public static String region() {
        return LOCALSTACK.getRegion();
    }

    /** @return cliente de SNS apuntando a LocalStack */
    public static SnsClient sns() {
        return SNS;
    }

    /** @return cliente de SQS apuntando a LocalStack */
    public static SqsClient sqs() {
        return SQS;
    }

    /** @return ARN del topic {@code fleetops_vehicle_events} */
    public static String topicArn() {
        return TOPIC_ARN;
    }

    /** @return URL de {@code vehicles_queue} */
    public static String queueUrl() {
        return QUEUE_URL;
    }

    /** @return URL de {@code vehicles_queue_dlq} */
    public static String dlqUrl() {
        return DLQ_URL;
    }

    /** @return URL de la cola de prueba suscrita al topic */
    public static String probeUrl() {
        return PROBE_URL;
    }

    /** Borra el topic: SNS "cae" y cada publicación falla. */
    public static void deleteTopic() {
        SNS.deleteTopic(request -> request.topicArn(TOPIC_ARN));
    }

    /** Vuelve a crear el topic (con el mismo ARN) y su suscripción de prueba: SNS "se recupera". */
    public static void restoreTopic() {
        createTopicAndProbeSubscription();
    }

    /** Deja vacías las colas de prueba. */
    public static void purgeQueues() {
        for (String url : new String[] {QUEUE_URL, DLQ_URL, PROBE_URL}) {
            SQS.purgeQueue(request -> request.queueUrl(url));
        }
    }

    private static String createTopicAndProbeSubscription() {
        String arn = SNS.createTopic(request -> request.name(TOPIC_NAME)).topicArn();
        SNS.subscribe(request -> request.topicArn(arn).protocol("sqs").endpoint(queueArn(PROBE_URL))
                .attributes(Map.of("RawMessageDelivery", "true")));
        return arn;
    }

    private static String queueArn(String queueUrl) {
        return SQS.getQueueAttributes(request -> request.queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)).attributes().get(QueueAttributeName.QUEUE_ARN);
    }
}
