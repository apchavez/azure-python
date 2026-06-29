package com.clinic.domain.ports;

import com.clinic.domain.entities.Appointment;

/**
 * Port for publishing appointment events to the messaging backbone.
 *
 * AWS project used SNS (topic) -> SQS (queue) for fan-out, and EventBridge
 * for the completion event. Here the adapter targets Azure Service Bus
 * (topic + queues) and Event Grid, but the application layer doesn't know that.
 */
public interface AppointmentEventPublisher {

    /** Publishes the "appointment created" event for country-specific processing. */
    void publishCreated(Appointment appointment);

    /** Publishes the "appointment completed" event (EventBridge -> Event Grid). */
    void publishCompleted(Appointment appointment);

    /** Publishes the "appointment cancelled" event. */
    void publishCancelled(Appointment appointment);
}
