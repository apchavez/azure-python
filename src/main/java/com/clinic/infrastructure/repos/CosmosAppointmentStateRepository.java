package com.clinic.infrastructure.repos;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.clinic.domain.entities.Appointment;
import com.clinic.domain.ports.AppointmentStateRepository;
import com.clinic.domain.shared.Page;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cosmos DB adapter implementing the state repository port.
 * This is the Azure equivalent of the AWS project's DynamoDB repository:
 * fast key-value state tracking for the pending/completed lifecycle.
 *
 * Authenticates via Managed Identity (DefaultAzureCredential) — no key in config.
 * Only this class knows about Cosmos. The domain/application layers depend
 * solely on the AppointmentStateRepository interface.
 */
public class CosmosAppointmentStateRepository implements AppointmentStateRepository {

    private final CosmosContainer container;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public CosmosAppointmentStateRepository(String endpoint, String databaseName,
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
    public void save(Appointment appointment) {
        resilient(() -> container.createItem(toItem(appointment)));
    }

    @Override
    public Optional<Appointment> findById(String appointmentId) {
        return resilient(() -> {
            try {
                AppointmentItem item = container
                        .readItem(appointmentId, new PartitionKey(appointmentId), AppointmentItem.class)
                        .getItem();
                return Optional.of(toDomain(item));
            } catch (CosmosException e) {
                if (e.getStatusCode() == 404) {
                    return Optional.empty();
                }
                throw new RuntimeException("Cosmos read failed (status " + e.getStatusCode() + "): " + e.getMessage(), e);
            }
        });
    }

    @Override
    public List<Appointment> findByInsuredId(String insuredId) {
        return resilient(() -> {
            SqlQuerySpec spec = new SqlQuerySpec(
                    "SELECT * FROM c WHERE c.insuredId = @insuredId",
                    new SqlParameter("@insuredId", insuredId));
            return container.queryItems(spec, new CosmosQueryRequestOptions(), AppointmentItem.class)
                    .stream()
                    .map(this::toDomain)
                    .toList();
        });
    }

    @Override
    public Page<Appointment> findByInsuredId(String insuredId, int pageSize, String continuationToken) {
        return resilient(() -> {
            SqlQuerySpec spec = new SqlQuerySpec(
                    "SELECT * FROM c WHERE c.insuredId = @insuredId",
                    new SqlParameter("@insuredId", insuredId));
            CosmosPagedIterable<AppointmentItem> paged =
                    container.queryItems(spec, new CosmosQueryRequestOptions(), AppointmentItem.class);
            Iterator<FeedResponse<AppointmentItem>> pages =
                    paged.iterableByPage(continuationToken, pageSize).iterator();
            if (!pages.hasNext()) {
                return new Page<>(List.of(), null);
            }
            FeedResponse<AppointmentItem> feedPage = pages.next();
            List<Appointment> items = feedPage.getResults().stream().map(this::toDomain).toList();
            return new Page<>(items, feedPage.getContinuationToken());
        });
    }

    @Override
    public void updateStatus(Appointment appointment) {
        resilient(() -> container.replaceItem(
                toItem(appointment),
                appointment.getAppointmentId(),
                new PartitionKey(appointment.getAppointmentId()),
                null));
    }

    private <T> T resilient(Supplier<T> operation) {
        return Retry.decorateSupplier(retry, circuitBreaker.decorateSupplier(operation)).get();
    }

    private void resilient(Runnable operation) {
        resilient(() -> { operation.run(); return null; });
    }

    public String ping() {
        try {
            container.read();
            return "UP";
        } catch (Exception e) {
            return "DOWN: " + e.getMessage();
        }
    }

    // --- mapping between domain entity and the Cosmos persistence model ---

    private AppointmentItem toItem(Appointment a) {
        AppointmentItem item = new AppointmentItem();
        item.id = a.getAppointmentId();
        item.insuredId = a.getInsuredId();
        item.scheduleId = a.getScheduleId();
        item.countryISO = a.getCountryISO().name();
        item.status = a.getStatus().name();
        item.createdAt = a.getCreatedAt() != null ? a.getCreatedAt().toString() : null;
        item.completedAt = a.getCompletedAt() != null ? a.getCompletedAt().toString() : null;
        item.cancelledAt = a.getCancelledAt() != null ? a.getCancelledAt().toString() : null;
        item.contactEmail = a.getContactEmail();
        return item;
    }

    private Appointment toDomain(AppointmentItem item) {
        Appointment a = new Appointment();
        a.setAppointmentId(item.id);
        a.setInsuredId(item.insuredId);
        a.setScheduleId(item.scheduleId);
        a.setCountryISO(com.clinic.domain.entities.CountryISO.valueOf(item.countryISO));
        a.setStatus(com.clinic.domain.entities.AppointmentStatus.valueOf(item.status));
        if (item.createdAt != null) a.setCreatedAt(java.time.Instant.parse(item.createdAt));
        if (item.completedAt != null) a.setCompletedAt(java.time.Instant.parse(item.completedAt));
        if (item.cancelledAt != null) a.setCancelledAt(java.time.Instant.parse(item.cancelledAt));
        a.setContactEmail(item.contactEmail);
        return a;
    }

    /** Plain persistence model (Cosmos serializes this to JSON). */
    public static class AppointmentItem {
        public String id;
        public String insuredId;
        public int scheduleId;
        public String countryISO;
        public String status;
        public String createdAt;
        public String completedAt;
        public String cancelledAt;
        public String contactEmail;
    }
}
