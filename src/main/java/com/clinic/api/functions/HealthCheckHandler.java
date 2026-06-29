package com.clinic.api.functions;

import com.clinic.infrastructure.config.AppContext;
import com.clinic.shared.HealthStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.Optional;

public class HealthCheckHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @FunctionName("health")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.GET},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "health"
            ) HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {

        try {
            HealthStatus status = AppContext.healthCheck();
            HttpStatus httpStatus = HealthStatus.UP.equals(status.status)
                    ? HttpStatus.OK
                    : HttpStatus.SERVICE_UNAVAILABLE;
            return request.createResponseBuilder(httpStatus)
                    .header("Content-Type", "application/json")
                    .body(MAPPER.writeValueAsString(status))
                    .build();
        } catch (Exception e) {
            context.getLogger().severe("Health check error: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("{\"error\":\"Health check unavailable\"}")
                    .build();
        }
    }
}
