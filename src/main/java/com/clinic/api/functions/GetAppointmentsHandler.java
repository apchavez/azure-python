package com.clinic.api.functions;

import com.clinic.domain.entities.Appointment;
import com.clinic.infrastructure.config.AppContext;
import com.clinic.shared.ApiResponse;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.List;
import java.util.Optional;

/**
 * Azure Functions HTTP entry point: GET /api/appointments/{insuredId}
 * Mirrors the AWS "list appointments by insured" query endpoint.
 */
public class GetAppointmentsHandler {

    @FunctionName("getAppointments")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.GET},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "appointments/{insuredId}"
            ) HttpRequestMessage<Optional<String>> request,
            @BindingName("insuredId") String insuredId,
            final ExecutionContext context) {

        try {
            if (insuredId == null || insuredId.isBlank()) {
                return ApiResponse.error(request, HttpStatus.BAD_REQUEST,
                        "insuredId path parameter is required");
            }
            List<Appointment> appointments = AppContext.getAppointments().byInsured(insuredId);
            return ApiResponse.ok(request, appointments);
        } catch (Exception e) {
            context.getLogger().severe("Error querying appointments: " + e.getMessage());
            return ApiResponse.error(request, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal error querying appointments");
        }
    }
}
