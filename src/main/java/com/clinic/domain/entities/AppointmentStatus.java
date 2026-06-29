package com.clinic.domain.entities;

/**
 * Appointment lifecycle: pending -> completed.
 * Mirrors the status tracking from the AWS project.
 */
public enum AppointmentStatus {
    PENDING,
    COMPLETED,
    CANCELLED,
    RESCHEDULED
}
