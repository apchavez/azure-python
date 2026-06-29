package com.clinic.domain.entities;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record of a single state transition in an appointment's lifecycle.
 * Written once, never updated — this is the "event" in lightweight event sourcing.
 */
public class AppointmentEvent {

    private String eventId;
    private String appointmentId;
    private String eventType;
    private String insuredId;
    private int scheduleId;
    private String countryISO;
    private String status;
    private Instant occurredAt;

    public AppointmentEvent() {}

    public static AppointmentEvent of(String eventType, Appointment appointment) {
        AppointmentEvent e = new AppointmentEvent();
        e.eventId = UUID.randomUUID().toString();
        e.appointmentId = appointment.getAppointmentId();
        e.eventType = eventType;
        e.insuredId = appointment.getInsuredId();
        e.scheduleId = appointment.getScheduleId();
        e.countryISO = appointment.getCountryISO().name();
        e.status = appointment.getStatus().name();
        e.occurredAt = Instant.now();
        return e;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getAppointmentId() { return appointmentId; }
    public void setAppointmentId(String appointmentId) { this.appointmentId = appointmentId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getInsuredId() { return insuredId; }
    public void setInsuredId(String insuredId) { this.insuredId = insuredId; }

    public int getScheduleId() { return scheduleId; }
    public void setScheduleId(int scheduleId) { this.scheduleId = scheduleId; }

    public String getCountryISO() { return countryISO; }
    public void setCountryISO(String countryISO) { this.countryISO = countryISO; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
