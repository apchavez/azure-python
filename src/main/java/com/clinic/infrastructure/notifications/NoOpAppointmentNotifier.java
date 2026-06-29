package com.clinic.infrastructure.notifications;

import com.clinic.domain.entities.Appointment;
import com.clinic.domain.ports.AppointmentNotifier;

/** No-op implementation used when ACS is not configured (ACS_ENDPOINT is absent). */
public class NoOpAppointmentNotifier implements AppointmentNotifier {
    @Override public void notifyCompleted(Appointment appointment) {}
    @Override public void notifyCancelled(Appointment appointment) {}
    @Override public void notifyRescheduled(Appointment old, Appointment newAppointment) {}
}
