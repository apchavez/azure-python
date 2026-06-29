package com.clinic.infrastructure.repos;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.clinic.domain.entities.AppointmentEvent;
import com.clinic.domain.ports.AppointmentEventStore;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Cosmos DB adapter for the append-only event store.
 * Writes to the appointment-events container (separate from the state container)
 * using appointmentId as the partition key so all events for one appointment
 * are co-located and can be retrieved in a single cross-partition-free query.
 *
 * Documents are written once and never updated — immutability is enforced by
 * always using createItem (never replaceItem).
 */
public class CosmosAppointmentEventStore implements AppointmentEventStore {

    private final CosmosContainer container;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public CosmosAppointmentEventStore(String endpoint, String databaseName,
                                       String containerName,
                                       Retry retry, CircuitBreaker circuitBreaker) {
        CosmosClient client = new CosmosClientBuilder()
                .endpoint(endpoint)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
        this.container = client.getDatabase(databaseName).getContainer(containerName);
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public void append(AppointmentEvent event) {
        resilient(() -> container.createItem(toItem(event), new PartitionKey(event.getAppointmentId()), null));
    }

    @Override
    public List<AppointmentEvent> findByAppointmentId(String appointmentId) {
        return resilient(() -> {
            SqlQuerySpec spec = new SqlQuerySpec(
                    "SELECT * FROM c WHERE c.appointmentId = @appointmentId ORDER BY c.occurredAt ASC",
                    new SqlParameter("@appointmentId", appointmentId));
            return container.queryItems(spec, new CosmosQueryRequestOptions(), EventItem.class)
                    .stream()
                    .map(this::toDomain)
                    .sorted(Comparator.comparing(AppointmentEvent::getOccurredAt))
                    .toList();
        });
    }

    private EventItem toItem(AppointmentEvent e) {
        EventItem item = new EventItem();
        item.id = e.getEventId();
        item.appointmentId = e.getAppointmentId();
        item.eventType = e.getEventType();
        item.insuredId = e.getInsuredId();
        item.scheduleId = e.getScheduleId();
        item.countryISO = e.getCountryISO();
        item.status = e.getStatus();
        item.occurredAt = e.getOccurredAt() != null ? e.getOccurredAt().toString() : null;
        return item;
    }

    private AppointmentEvent toDomain(EventItem item) {
        AppointmentEvent e = new AppointmentEvent();
        e.setEventId(item.id);
        e.setAppointmentId(item.appointmentId);
        e.setEventType(item.eventType);
        e.setInsuredId(item.insuredId);
        e.setScheduleId(item.scheduleId);
        e.setCountryISO(item.countryISO);
        e.setStatus(item.status);
        if (item.occurredAt != null) e.setOccurredAt(Instant.parse(item.occurredAt));
        return e;
    }

    private <T> T resilient(Supplier<T> operation) {
        return Retry.decorateSupplier(retry, circuitBreaker.decorateSupplier(operation)).get();
    }

    private void resilient(Runnable operation) {
        resilient(() -> { operation.run(); return null; });
    }

    public static class EventItem {
        public String id;
        public String appointmentId;
        public String eventType;
        public String insuredId;
        public int scheduleId;
        public String countryISO;
        public String status;
        public String occurredAt;
    }
}
