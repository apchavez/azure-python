package com.clinic.api.functions;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.ServiceBusTopicTrigger;

/** CL country worker — triggered by the 'cl-worker' subscription on the 'appointment-created' topic. */
public class AppointmentWorkerCL extends AppointmentWorkerBase {

    @Override
    protected String country() { return "CL"; }

    @FunctionName("appointmentWorkerCL")
    public void run(
            @ServiceBusTopicTrigger(
                    name = "message",
                    topicName = "appointment-created",
                    subscriptionName = "cl-worker",
                    connection = "SERVICEBUS"
            ) String message,
            final ExecutionContext context) {
        process(message, context);
    }
}
