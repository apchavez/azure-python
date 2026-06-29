package com.clinic.api.functions;

import com.clinic.domain.entities.Appointment;
import com.clinic.infrastructure.config.AppContext;
import com.clinic.shared.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.Map;
import java.util.Optional;

/**
 * Azure Functions HTTP entry point: PATCH /api/appointments/{appointmentId}/reschedule
 *
 * Marks the existing PENDING appointment as RESCHEDULED and creates a new
 * PENDING appointment for the new schedule slot, which then flows through
 * the normal event-driven processing pipeline.
 *
 * Request body: { "newScheduleId": <int> }
 */
public class RescheduleAppointmentHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @FunctionName("rescheduleAppointment")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.PATCH},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "appointments/{appointmentId}/reschedule"
            ) HttpRequestMessage<Optional<String>> request,
            @BindingName("appointmentId") String appointmentId,
            final ExecutionContext context) {

        try {
            if (appointmentId == null || appointmentId.isBlank()) {
                return ApiResponse.error(request, HttpStatus.BAD_REQUEST,
                        "appointmentId path parameter is required");
            }
            String body = request.getBody().orElse("");
            if (body.isBlank()) {
                return ApiResponse.error(request, HttpStatus.BAD_REQUEST, "Request body is required");
            }
            JsonNode node = MAPPER.readTree(body);
            JsonNode scheduleNode = node.get("newScheduleId");
            if (scheduleNode == null || !scheduleNode.isInt() || scheduleNode.asInt() < 1) {
                return ApiResponse.error(request, HttpStatus.BAD_REQUEST,
                        "newScheduleId (integer >= 1) is required");
            }
            int newScheduleId = scheduleNode.asInt();

            Appointment newAppointment = AppContext.rescheduleAppointment().execute(appointmentId, newScheduleId);

            context.getLogger().info(String.format(
                    "appointment.rescheduled oldId=%s newId=%s newScheduleId=%d invocationId=%s",
                    appointmentId, newAppointment.getAppointmentId(), newScheduleId,
                    context.getInvocationId()));

            return ApiResponse.accepted(request, Map.of(
                    "message", "Appointment rescheduled",
                    "newAppointmentId", newAppointment.getAppointmentId(),
                    "newScheduleId", newScheduleId));
        } catch (IllegalStateException e) {
            String msg = e.getMessage();
            if (msg != null && msg.startsWith("Appointment not found")) {
                return ApiResponse.error(request, HttpStatus.NOT_FOUND, msg);
            }
            return ApiResponse.error(request, HttpStatus.CONFLICT, msg);
        } catch (Exception e) {
            context.getLogger().severe("Error rescheduling appointment: " + e.getMessage());
            return ApiResponse.error(request, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal error rescheduling appointment");
        }
    }
}
