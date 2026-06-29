package com.clinic.api.functions;

/**
 * Input DTO for POST /appointments. Mirrors the AWS request body:
 * { "insuredId": "12345", "scheduleId": 10, "countryISO": "PE" }
 *
 * Validation is performed at the edge inside CreateAppointmentHandler
 * (no Bean Validation dependency in the native Functions build).
 */
public class CreateAppointmentRequest {
    public String insuredId;
    public int scheduleId;
    public String countryISO;
    public String contactEmail;
}
