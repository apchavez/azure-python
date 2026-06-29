package com.clinic.domain.entities;

import java.time.Instant;

/**
 * Core domain entity — mirrors the TypeScript "Appointment" entity from the
 * AWS project. Pure business object: no framework, no Azure, no persistence
 * concerns. This is the heart of the Clean Architecture domain layer.
 */
public class Appointment {

    private String appointmentId;
    private String insuredId;
    private int scheduleId;
    private CountryISO countryISO;
    private AppointmentStatus status;
    private Instant createdAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private String contactEmail;

    public Appointment() {
    }

    public Appointment(String appointmentId, String insuredId, int scheduleId, CountryISO countryISO) {
        this.appointmentId = appointmentId;
        this.insuredId = insuredId;
        this.scheduleId = scheduleId;
        this.countryISO = countryISO;
        this.status = AppointmentStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void markCompleted() {
        if (this.status != AppointmentStatus.PENDING) {
            throw new IllegalStateException(
                    "Only a PENDING appointment can be completed (current: " + this.status + ")");
        }
        this.status = AppointmentStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void markCancelled() {
        if (this.status != AppointmentStatus.PENDING) {
            throw new IllegalStateException(
                    "Only a PENDING appointment can be cancelled (current: " + this.status + ")");
        }
        this.status = AppointmentStatus.CANCELLED;
        this.cancelledAt = Instant.now();
    }

    public void markRescheduled() {
        if (this.status != AppointmentStatus.PENDING) {
            throw new IllegalStateException(
                    "Only a PENDING appointment can be rescheduled (current: " + this.status + ")");
        }
        this.status = AppointmentStatus.RESCHEDULED;
    }

    public String getAppointmentId() { return appointmentId; }
    public void setAppointmentId(String appointmentId) { this.appointmentId = appointmentId; }

    public String getInsuredId() { return insuredId; }
    public void setInsuredId(String insuredId) { this.insuredId = insuredId; }

    public int getScheduleId() { return scheduleId; }
    public void setScheduleId(int scheduleId) { this.scheduleId = scheduleId; }

    public CountryISO getCountryISO() { return countryISO; }
    public void setCountryISO(CountryISO countryISO) { this.countryISO = countryISO; }

    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
}
