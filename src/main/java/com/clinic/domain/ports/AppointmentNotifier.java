package com.clinic.domain.ports;

import com.clinic.domain.entities.Appointment;

/**
 * Port for sending notifications to the insured party.
 * The domain/application layer depends only on this interface; the infrastructure
 * adapter targets Azure Communication Services Email without the domain knowing.
 *
 * Implementations are expected to be best-effort: a notification failure must
 * NOT propagate to the caller — the appointment lifecycle takes precedence.
 */
public interface AppointmentNotifier {

    void notifyCompleted(Appointment appointment);

    void notifyCancelled(Appointment appointment);

    void notifyRescheduled(Appointment old, Appointment newAppointment);
}
