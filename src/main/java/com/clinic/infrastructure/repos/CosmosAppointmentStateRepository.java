package com.clinic.infrastructure.repos;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.PartitionKey;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.clinic.domain.entities.Appointment;
import com.clinic.domain.ports.AppointmentStateRepository;

import java.util.List;
import java.util.Optional;

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

    public CosmosAppointmentStateRepository(String endpoint,
                                            String databaseName, String containerName) {
        CosmosClient client = new CosmosClientBuilder()
                .endpoint(endpoint)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
        this.container = client.getDatabase(databaseName).getContainer(containerName);
    }

    @Override
    public void save(Appointment appointment) {
        container.createItem(toItem(appointment));
    }

    @Override
    public Optional<Appointment> findById(String appointmentId) {
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
    }

    @Override
    public List<Appointment> findByInsuredId(String insuredId) {
        com.azure.cosmos.models.CosmosQueryRequestOptions options =
                new com.azure.cosmos.models.CosmosQueryRequestOptions();
        com.azure.cosmos.models.SqlQuerySpec spec = new com.azure.cosmos.models.SqlQuerySpec(
                "SELECT * FROM c WHERE c.insuredId = @insuredId",
                new com.azure.cosmos.models.SqlParameter("@insuredId", insuredId));
        return container.queryItems(spec, options, AppointmentItem.class)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void updateStatus(Appointment appointment) {
        container.replaceItem(
                toItem(appointment),
                appointment.getAppointmentId(),
                new PartitionKey(appointment.getAppointmentId()),
                null);
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
    }
}
