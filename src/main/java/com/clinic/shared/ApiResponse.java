package com.clinic.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;

import java.util.Optional;

/** Centralized HTTP response builder to avoid raw JSON string construction in handlers. */
public final class ApiResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ApiResponse() {}

    public static HttpResponseMessage error(HttpRequestMessage<Optional<String>> request,
                                            HttpStatus status, String message) {
        ObjectNode body = MAPPER.createObjectNode().put("error", message);
        try {
            return request.createResponseBuilder(status)
                    .header("Content-Type", "application/json")
                    .body(MAPPER.writeValueAsString(body))
                    .build();
        } catch (Exception e) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("{\"error\":\"Internal serialization failure\"}")
                    .build();
        }
    }

    public static HttpResponseMessage ok(HttpRequestMessage<Optional<String>> request,
                                         Object payload) {
        try {
            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(MAPPER.writeValueAsString(payload))
                    .build();
        } catch (Exception e) {
            return error(request, HttpStatus.INTERNAL_SERVER_ERROR, "Response serialization failed");
        }
    }

    public static HttpResponseMessage accepted(HttpRequestMessage<Optional<String>> request,
                                               Object payload) {
        try {
            return request.createResponseBuilder(HttpStatus.ACCEPTED)
                    .header("Content-Type", "application/json")
                    .body(MAPPER.writeValueAsString(payload))
                    .build();
        } catch (Exception e) {
            return error(request, HttpStatus.INTERNAL_SERVER_ERROR, "Response serialization failed");
        }
    }
}
