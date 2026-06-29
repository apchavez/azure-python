package com.clinic.domain.ports;

import com.clinic.domain.entities.AppointmentEvent;

import java.util.List;

/**
 * Port for the append-only event log.
 * Each state transition produces one immutable AppointmentEvent document.
 * Implemented by a Cosmos DB adapter targeting the appointment-events container.
 */
public interface AppointmentEventStore {

    void append(AppointmentEvent event);

    List<AppointmentEvent> findByAppointmentId(String appointmentId);
}
