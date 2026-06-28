package com.clinic.infrastructure.config;

import com.clinic.application.usecases.CreateAppointmentUseCase;
import com.clinic.application.usecases.GetAppointmentsUseCase;
import com.clinic.application.usecases.ProcessAppointmentUseCase;
import com.clinic.domain.ports.AppointmentEventPublisher;
import com.clinic.domain.ports.AppointmentRelationalRepository;
import com.clinic.domain.ports.AppointmentStateRepository;
import com.clinic.infrastructure.messaging.ServiceBusEventPublisher;
import com.clinic.infrastructure.repos.AzureSqlAppointmentRepository;
import com.clinic.infrastructure.repos.CosmosAppointmentStateRepository;

/**
 * Manual composition root (no Spring). Works with the native Azure Functions
 * Java worker. Adapters and use cases are created once (lazy singletons) and
 * reused across warm invocations, reading configuration from environment
 * variables (the Function App's app settings).
 */
public final class AppContext {

    private static volatile AppointmentStateRepository stateRepo;
    private static volatile AppointmentEventPublisher publisher;
    private static volatile AppointmentRelationalRepository relationalRepo;
    private static volatile CreateAppointmentUseCase createUseCase;
    private static volatile ProcessAppointmentUseCase processUseCase;
    private static volatile GetAppointmentsUseCase getUseCase;

    private AppContext() {
    }

    // --- shared adapters (built once) ---

    private static AppointmentStateRepository stateRepository() {
        if (stateRepo == null) {
            synchronized (AppContext.class) {
                if (stateRepo == null) {
                    stateRepo = new CosmosAppointmentStateRepository(
                            env("COSMOS_ENDPOINT", ""),
                            env("COSMOS_DATABASE", "clinicdb"),
                            env("COSMOS_CONTAINER", "appointments"));
                }
            }
        }
        return stateRepo;
    }

    private static AppointmentEventPublisher eventPublisher() {
        if (publisher == null) {
            synchronized (AppContext.class) {
                if (publisher == null) {
                    publisher = new ServiceBusEventPublisher(
                            env("SERVICEBUS__fullyQualifiedNamespace", ""),
                            env("SERVICEBUS_CREATED_TOPIC", "appointment-created"),
                            env("SERVICEBUS_COMPLETED_TOPIC", "appointment-completed"));
                }
            }
        }
        return publisher;
    }

    private static AppointmentRelationalRepository relationalRepository() {
        if (relationalRepo == null) {
            synchronized (AppContext.class) {
                if (relationalRepo == null) {
                    relationalRepo = new AzureSqlAppointmentRepository(
                            env("SQL_HOST", ""),
                            env("SQL_DATABASE", "clinicdb"),
                            env("SQL_USER", ""),
                            env("SQL_PASSWORD", ""));
                }
            }
        }
        return relationalRepo;
    }

    // --- use cases ---

    public static CreateAppointmentUseCase createAppointment() {
        if (createUseCase == null) {
            synchronized (AppContext.class) {
                if (createUseCase == null) {
                    createUseCase = new CreateAppointmentUseCase(stateRepository(), eventPublisher());
                }
            }
        }
        return createUseCase;
    }

    public static ProcessAppointmentUseCase processAppointment() {
        if (processUseCase == null) {
            synchronized (AppContext.class) {
                if (processUseCase == null) {
                    processUseCase = new ProcessAppointmentUseCase(
                            stateRepository(), relationalRepository(), eventPublisher());
                }
            }
        }
        return processUseCase;
    }

    public static GetAppointmentsUseCase getAppointments() {
        if (getUseCase == null) {
            synchronized (AppContext.class) {
                if (getUseCase == null) {
                    getUseCase = new GetAppointmentsUseCase(stateRepository());
                }
            }
        }
        return getUseCase;
    }

    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }
}
