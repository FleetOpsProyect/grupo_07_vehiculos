package co.edu.univalle.fleetops.vehicles.infrastructure.messaging;

import co.edu.univalle.fleetops.vehicles.application.event.PendingEvent;
import co.edu.univalle.fleetops.vehicles.application.port.out.EventPublisher;
import java.util.Map;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;

/**
 * Publica el sobre tal cual en el topic {@code fleetops_vehicle_events}, con el atributo
 * {@code event_type} que usan las políticas de filtro de los suscriptores.
 */
public class SnsEventPublisher implements EventPublisher {

    private final SnsClient sns;
    private final String topicArn;

    /**
     * Crea el publicador.
     *
     * @param sns cliente de SNS
     * @param topicArn ARN del topic de Vehículos
     */
    public SnsEventPublisher(SnsClient sns, String topicArn) {
        this.sns = sns;
        this.topicArn = topicArn;
    }

    @Override
    public void publish(PendingEvent event) {
        sns.publish(request -> request
                .topicArn(topicArn)
                .message(event.payload())
                .messageAttributes(Map.of("event_type", MessageAttributeValue.builder()
                        .dataType("String").stringValue(event.eventType()).build())));
    }
}
