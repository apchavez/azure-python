package com.clinic.infrastructure.messaging;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.clinic.domain.entities.Appointment;
import com.clinic.domain.ports.AppointmentEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Service Bus adapter implementing the event publisher port.
 * Azure equivalent of the AWS project's SNS+SQS / EventBridge publishing.
 *
 * Authenticates via Managed Identity (DefaultAzureCredential) using the
 * fully-qualified namespace name (SERVICEBUS_FQNS). No connection string stored in config.
 * Uses a topic so multiple subscribers (country workers) can fan out.
 */
public class ServiceBusEventPublisher implements AppointmentEventPublisher {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ServiceBusSenderClient createdSender;
    private final ServiceBusSenderClient completedSender;

    public ServiceBusEventPublisher(String fullyQualifiedNamespace,
                                    String createdTopic,
                                    String completedTopic) {
        var credential = new DefaultAzureCredentialBuilder().build();
        this.createdSender = new ServiceBusClientBuilder()
                .fullyQualifiedNamespace(fullyQualifiedNamespace)
                .credential(credential)
                .sender()
                .topicName(createdTopic)
                .buildClient();
        this.completedSender = new ServiceBusClientBuilder()
                .fullyQualifiedNamespace(fullyQualifiedNamespace)
                .credential(credential)
                .sender()
                .topicName(completedTopic)
                .buildClient();
    }

    @Override
    public void publishCreated(Appointment a) {
        try {
            ObjectNode node = MAPPER.createObjectNode()
                    .put("eventType", "APPOINTMENT_CREATED")
                    .put("appointmentId", a.getAppointmentId())
                    .put("insuredId", a.getInsuredId())
                    .put("scheduleId", a.getScheduleId())
                    .put("countryISO", a.getCountryISO().name());
            ServiceBusMessage message = new ServiceBusMessage(MAPPER.writeValueAsString(node));
            // Subject lets subscribers filter by country (PE / CL) at the subscription level.
            message.setSubject(a.getCountryISO().name());
            createdSender.sendMessage(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish APPOINTMENT_CREATED event", e);
        }
    }

    @Override
    public void publishCompleted(Appointment a) {
        try {
            ObjectNode node = MAPPER.createObjectNode()
                    .put("eventType", "APPOINTMENT_COMPLETED")
                    .put("appointmentId", a.getAppointmentId())
                    .put("insuredId", a.getInsuredId());
            completedSender.sendMessage(new ServiceBusMessage(MAPPER.writeValueAsString(node)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish APPOINTMENT_COMPLETED event", e);
        }
    }
}
