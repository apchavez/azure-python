package com.clinic.infrastructure.messaging;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder;
import com.clinic.domain.entities.Appointment;
import com.clinic.domain.ports.AppointmentEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;

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
    private final ServiceBusSenderClient cancelledSender;
    private final String fullyQualifiedNamespace;
    private final TokenCredential credential;

    public ServiceBusEventPublisher(String fullyQualifiedNamespace,
                                    String createdTopic,
                                    String completedTopic,
                                    String cancelledTopic) {
        this.fullyQualifiedNamespace = fullyQualifiedNamespace;
        this.credential = new DefaultAzureCredentialBuilder().build();
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
        this.cancelledSender = new ServiceBusClientBuilder()
                .fullyQualifiedNamespace(fullyQualifiedNamespace)
                .credential(credential)
                .sender()
                .topicName(cancelledTopic)
                .buildClient();
    }

    public String ping() {
        try {
            new ServiceBusAdministrationClientBuilder()
                    .endpoint("https://" + fullyQualifiedNamespace)
                    .credential(credential)
                    .buildClient()
                    .getNamespaceProperties();
            return "UP";
        } catch (Exception e) {
            return "DOWN: " + e.getMessage();
        }
    }

    @Override
    public void publishCreated(Appointment a) {
        try {
            ObjectNode node = MAPPER.createObjectNode()
                    .put("eventType", "APPOINTMENT_CREATED")
                    .put("appointmentId", a.getAppointmentId())
                    .put("correlationId", a.getAppointmentId())
                    .put("insuredId", a.getInsuredId())
                    .put("scheduleId", a.getScheduleId())
                    .put("countryISO", a.getCountryISO().name())
                    .put("occurredAt", Instant.now().toString());
            ServiceBusMessage message = new ServiceBusMessage(MAPPER.writeValueAsString(node));
            // Subject lets subscribers filter by country (PE / CL) at the subscription level.
            message.setSubject(a.getCountryISO().name());
            message.setCorrelationId(a.getAppointmentId());
            message.getApplicationProperties().put("eventType", "APPOINTMENT_CREATED");
            message.getApplicationProperties().put("countryISO", a.getCountryISO().name());
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
                    .put("correlationId", a.getAppointmentId())
                    .put("insuredId", a.getInsuredId())
                    .put("countryISO", a.getCountryISO().name())
                    .put("occurredAt", Instant.now().toString());
            ServiceBusMessage message = new ServiceBusMessage(MAPPER.writeValueAsString(node));
            message.setSubject(a.getCountryISO().name());
            message.setCorrelationId(a.getAppointmentId());
            message.getApplicationProperties().put("eventType", "APPOINTMENT_COMPLETED");
            message.getApplicationProperties().put("countryISO", a.getCountryISO().name());
            completedSender.sendMessage(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish APPOINTMENT_COMPLETED event", e);
        }
    }

    @Override
    public void publishCancelled(Appointment a) {
        try {
            ObjectNode node = MAPPER.createObjectNode()
                    .put("eventType", "APPOINTMENT_CANCELLED")
                    .put("appointmentId", a.getAppointmentId())
                    .put("correlationId", a.getAppointmentId())
                    .put("insuredId", a.getInsuredId())
                    .put("countryISO", a.getCountryISO().name())
                    .put("occurredAt", Instant.now().toString());
            ServiceBusMessage message = new ServiceBusMessage(MAPPER.writeValueAsString(node));
            message.setSubject(a.getCountryISO().name());
            message.setCorrelationId(a.getAppointmentId());
            message.getApplicationProperties().put("eventType", "APPOINTMENT_CANCELLED");
            message.getApplicationProperties().put("countryISO", a.getCountryISO().name());
            cancelledSender.sendMessage(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish APPOINTMENT_CANCELLED event", e);
        }
    }
}
