package com.clinic.api.functions;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.ServiceBusTopicTrigger;

/** PE country worker — triggered by the 'pe-worker' subscription on the 'appointment-created' topic. */
public class AppointmentWorkerPE extends AppointmentWorkerBase {

    @Override
    protected String country() { return "PE"; }

    @FunctionName("appointmentWorkerPE")
    public void run(
            @ServiceBusTopicTrigger(
                    name = "message",
                    topicName = "appointment-created",
                    subscriptionName = "pe-worker",
                    connection = "SERVICEBUS"
            ) String message,
            final ExecutionContext context) {
        process(message, context);
    }
}
