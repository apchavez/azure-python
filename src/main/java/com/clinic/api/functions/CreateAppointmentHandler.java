package com.clinic.api.functions;

import com.clinic.domain.entities.Appointment;
import com.clinic.domain.entities.CountryISO;
import com.clinic.infrastructure.config.AppContext;
import com.clinic.shared.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.Optional;

/**
 * Azure Functions HTTP entry point: POST /api/appointments
 * Equivalent to the AWS API Gateway -> Lambda "createAppointment" handler.
 *
 * Adapts HTTP <-> domain and delegates to the use case resolved by AppContext.
 * The domain, use cases and adapters are unchanged — only this entry layer.
 */
public class CreateAppointmentHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @FunctionName("createAppointment")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "appointments"
            ) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        try {
            String body = request.getBody().orElse("");
            CreateAppointmentRequest req = MAPPER.readValue(body, CreateAppointmentRequest.class);

            if (req.insuredId == null || req.insuredId.isBlank()
                    || req.scheduleId < 1
                    || req.countryISO == null
                    || !CountryISO.isSupported(req.countryISO)) {
                return ApiResponse.error(request, HttpStatus.BAD_REQUEST,
                        "Invalid request: insuredId, scheduleId>=1 and countryISO in ["
                                + CountryISO.supportedValues() + "] are required");
            }

            Appointment appointment = AppContext.createAppointment()
                    .execute(req.insuredId, req.scheduleId, CountryISO.valueOf(req.countryISO), req.contactEmail);
            context.getLogger().info(String.format(
                    "appointment.accepted appointmentId=%s insuredId=%s countryISO=%s invocationId=%s",
                    appointment.getAppointmentId(), appointment.getInsuredId(),
                    appointment.getCountryISO().name(), context.getInvocationId()));
            return ApiResponse.accepted(request, CreateAppointmentResponse.received(appointment.getAppointmentId()));

        } catch (Exception e) {
            context.getLogger().severe("Error creating appointment: " + e.getMessage());
            return ApiResponse.error(request, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal error processing appointment");
        }
    }
}
