package com.clinic.api.functions;

import com.clinic.infrastructure.config.AppContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;

/**
 * Shared processing logic for country-specific Service Bus workers.
 * Each country worker (PE, CL) extends this class and provides its country code
 * so routing is handled here once rather than duplicated in each subclass.
 */
abstract class AppointmentWorkerBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected abstract String country();

    protected void process(String message, ExecutionContext context) {
        try {
            JsonNode node = MAPPER.readTree(message);
            // Routing is enforced at the Service Bus subscription level (SQL filter on sys.Subject).
            // This worker only receives messages already filtered for its country.
            String appointmentId = node.path("appointmentId").asText();
            context.getLogger().info("[" + country() + "] Processing appointment " + appointmentId);
            AppContext.processAppointment().execute(appointmentId);
            context.getLogger().info("[" + country() + "] Completed appointment " + appointmentId);
        } catch (Exception e) {
            context.getLogger().severe("[" + country() + "] Error processing message: " + e.getMessage());
            throw new RuntimeException(e); // let Service Bus retry / dead-letter
        }
    }
}
