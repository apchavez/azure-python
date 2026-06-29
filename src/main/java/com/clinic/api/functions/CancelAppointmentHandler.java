package com.clinic.api.functions;

import com.clinic.infrastructure.config.AppContext;
import com.clinic.shared.ApiResponse;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.Map;
import java.util.Optional;

/**
 * Azure Functions HTTP entry point: DELETE /api/appointments/{appointmentId}
 * Cancels a PENDING appointment and publishes an APPOINTMENT_CANCELLED event.
 */
public class CancelAppointmentHandler {

    @FunctionName("cancelAppointment")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.DELETE},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "appointments/{appointmentId}"
            ) HttpRequestMessage<Optional<String>> request,
            @BindingName("appointmentId") String appointmentId,
            final ExecutionContext context) {

        try {
            if (appointmentId == null || appointmentId.isBlank()) {
                return ApiResponse.error(request, HttpStatus.BAD_REQUEST,
                        "appointmentId path parameter is required");
            }
            AppContext.cancelAppointment().execute(appointmentId);
            context.getLogger().info(String.format(
                    "appointment.cancelled appointmentId=%s invocationId=%s",
                    appointmentId, context.getInvocationId()));
            return ApiResponse.ok(request,
                    Map.of("message", "Appointment cancelled", "appointmentId", appointmentId));
        } catch (IllegalStateException e) {
            String msg = e.getMessage();
            if (msg != null && msg.startsWith("Appointment not found")) {
                return ApiResponse.error(request, HttpStatus.NOT_FOUND, msg);
            }
            return ApiResponse.error(request, HttpStatus.CONFLICT, msg);
        } catch (Exception e) {
            context.getLogger().severe("Error cancelling appointment: " + e.getMessage());
            return ApiResponse.error(request, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal error cancelling appointment");
        }
    }
}
