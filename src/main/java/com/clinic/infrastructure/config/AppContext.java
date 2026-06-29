package com.clinic.infrastructure.config;

import com.clinic.application.usecases.CancelAppointmentUseCase;
import com.clinic.application.usecases.CreateAppointmentUseCase;
import com.clinic.application.usecases.GetAppointmentsUseCase;
import com.clinic.application.usecases.ProcessAppointmentUseCase;
import com.clinic.application.usecases.RescheduleAppointmentUseCase;
import com.clinic.domain.ports.AppointmentEventStore;
import com.clinic.domain.ports.AppointmentNotifier;
import com.clinic.infrastructure.messaging.ServiceBusEventPublisher;
import com.clinic.infrastructure.notifications.AcsAppointmentNotifier;
import com.clinic.infrastructure.notifications.NoOpAppointmentNotifier;
import com.clinic.infrastructure.repos.AzureSqlAppointmentRepository;
import com.clinic.infrastructure.repos.CosmosAppointmentEventStore;
import com.clinic.infrastructure.repos.CosmosAppointmentStateRepository;
import com.clinic.shared.HealthStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manual composition root (no Spring). Works with the native Azure Functions
 * Java worker. Adapters and use cases are created once (lazy singletons) and
 * reused across warm invocations, reading configuration from environment
 * variables (the Function App's app settings).
 */
public final class AppContext {

    private static volatile CosmosAppointmentStateRepository stateRepo;
    private static volatile ServiceBusEventPublisher publisher;
    private static volatile AzureSqlAppointmentRepository relationalRepo;
    private static volatile CreateAppointmentUseCase createUseCase;
    private static volatile ProcessAppointmentUseCase processUseCase;
    private static volatile GetAppointmentsUseCase getUseCase;
    private static volatile CancelAppointmentUseCase cancelUseCase;
    private static volatile RescheduleAppointmentUseCase rescheduleUseCase;
    private static volatile AppointmentNotifier notifier;
    private static volatile CosmosAppointmentEventStore cosmosEventStore;

    private AppContext() {
    }

    // --- shared adapters (built once) ---

    private static CosmosAppointmentStateRepository stateRepository() {
        if (stateRepo == null) {
            synchronized (AppContext.class) {
                if (stateRepo == null) {
                    stateRepo = new CosmosAppointmentStateRepository(
                            env("COSMOS_ENDPOINT", ""),
                            env("COSMOS_DATABASE", "clinicdb"),
                            env("COSMOS_CONTAINER", "appointments"),
                            ResilienceConfig.exponentialRetry("cosmos"),
                            ResilienceConfig.circuitBreaker("cosmos"));
                }
            }
        }
        return stateRepo;
    }

    private static ServiceBusEventPublisher eventPublisher() {
        if (publisher == null) {
            synchronized (AppContext.class) {
                if (publisher == null) {
                    publisher = new ServiceBusEventPublisher(
                            env("SERVICEBUS__fullyQualifiedNamespace", ""),
                            env("SERVICEBUS_CREATED_TOPIC", "appointment-created"),
                            env("SERVICEBUS_COMPLETED_TOPIC", "appointment-completed"),
                            env("SERVICEBUS_CANCELLED_TOPIC", "appointment-cancelled"),
                            ResilienceConfig.exponentialRetry("servicebus"),
                            ResilienceConfig.circuitBreaker("servicebus"));
                }
            }
        }
        return publisher;
    }

    private static AzureSqlAppointmentRepository relationalRepository() {
        if (relationalRepo == null) {
            synchronized (AppContext.class) {
                if (relationalRepo == null) {
                    relationalRepo = new AzureSqlAppointmentRepository(
                            env("SQL_HOST", ""),
                            env("SQL_DATABASE", "clinicdb"),
                            env("SQL_AUTHENTICATION", "SqlPassword"),
                            env("SQL_USER", ""),
                            env("SQL_PASSWORD", ""),
                            ResilienceConfig.exponentialRetry("sql"),
                            ResilienceConfig.circuitBreaker("sql"));
                }
            }
        }
        return relationalRepo;
    }

    public static AppointmentEventStore eventStore() {
        if (cosmosEventStore == null) {
            synchronized (AppContext.class) {
                if (cosmosEventStore == null) {
                    cosmosEventStore = new CosmosAppointmentEventStore(
                            env("COSMOS_ENDPOINT", ""),
                            env("COSMOS_DATABASE", "clinicdb"),
                            env("COSMOS_EVENTS_CONTAINER", "appointment-events"),
                            ResilienceConfig.exponentialRetry("cosmos-events"),
                            ResilienceConfig.circuitBreaker("cosmos-events"));
                }
            }
        }
        return cosmosEventStore;
    }

    private static AppointmentNotifier notifier() {
        if (notifier == null) {
            synchronized (AppContext.class) {
                if (notifier == null) {
                    String endpoint = env("ACS_ENDPOINT", "");
                    String sender = env("ACS_SENDER_ADDRESS", "");
                    notifier = endpoint.isBlank()
                            ? new NoOpAppointmentNotifier()
                            : new AcsAppointmentNotifier(endpoint, sender);
                }
            }
        }
        return notifier;
    }

    // --- use cases ---

    public static CreateAppointmentUseCase createAppointment() {
        if (createUseCase == null) {
            synchronized (AppContext.class) {
                if (createUseCase == null) {
                    createUseCase = new CreateAppointmentUseCase(stateRepository(), eventPublisher(), eventStore());
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
                            stateRepository(), relationalRepository(), eventPublisher(), notifier(), eventStore());
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

    public static CancelAppointmentUseCase cancelAppointment() {
        if (cancelUseCase == null) {
            synchronized (AppContext.class) {
                if (cancelUseCase == null) {
                    cancelUseCase = new CancelAppointmentUseCase(stateRepository(), eventPublisher(), notifier(), eventStore());
                }
            }
        }
        return cancelUseCase;
    }

    public static RescheduleAppointmentUseCase rescheduleAppointment() {
        if (rescheduleUseCase == null) {
            synchronized (AppContext.class) {
                if (rescheduleUseCase == null) {
                    rescheduleUseCase = new RescheduleAppointmentUseCase(stateRepository(), eventPublisher(), notifier(), eventStore());
                }
            }
        }
        return rescheduleUseCase;
    }

    public static HealthStatus healthCheck() {
        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("cosmosDb", stateRepository().ping());
        checks.put("azureSql", relationalRepository().ping());
        checks.put("serviceBus", eventPublisher().ping());
        boolean allUp = checks.values().stream().allMatch("UP"::equals);
        return new HealthStatus(allUp ? HealthStatus.UP : HealthStatus.DOWN, checks);
    }

    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }
}
