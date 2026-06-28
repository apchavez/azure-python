package com.clinic.api.functions;

/**
 * Response DTO. Mirrors the AWS response:
 * { "message": "Appointment received", "status": "pending" }
 */
public record CreateAppointmentResponse(String appointmentId, String message, String status) {

    public static CreateAppointmentResponse received(String appointmentId) {
        return new CreateAppointmentResponse(appointmentId, "Appointment received", "pending");
    }
}
