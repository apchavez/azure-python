package com.clinic.application;

import com.clinic.application.usecases.CreateAppointmentUseCase;
import com.clinic.domain.entities.Appointment;
import com.clinic.domain.entities.AppointmentStatus;
import com.clinic.domain.entities.CountryISO;
import com.clinic.domain.ports.AppointmentEventPublisher;
import com.clinic.domain.ports.AppointmentStateRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for the create use case using in-memory fakes for the ports.
 * No Azure needed — this is the payoff of Clean Architecture: the business
 * logic is testable in isolation. Equivalent to the Jest unit tests in AWS.
 */
class CreateAppointmentUseCaseTest {

    static class InMemoryState implements AppointmentStateRepository {
        final List<Appointment> saved = new ArrayList<>();
        public void save(Appointment a) { saved.add(a); }
        public Optional<Appointment> findById(String id) {
            return saved.stream().filter(x -> x.getAppointmentId().equals(id)).findFirst();
        }
        public List<Appointment> findByInsuredId(String insuredId) {
            return saved.stream().filter(x -> x.getInsuredId().equals(insuredId)).toList();
        }
        public void updateStatus(Appointment a) { }
    }

    static class CapturingPublisher implements AppointmentEventPublisher {
        Appointment createdEvent;
        public void publishCreated(Appointment a) { createdEvent = a; }
        public void publishCompleted(Appointment a) { }
    }

    @Test
    void createsPendingAppointmentAndPublishesEvent() {
        InMemoryState state = new InMemoryState();
        CapturingPublisher publisher = new CapturingPublisher();
        CreateAppointmentUseCase useCase = new CreateAppointmentUseCase(state, publisher);

        Appointment result = useCase.execute("12345", 10, CountryISO.PE);

        assertNotNull(result.getAppointmentId());
        assertEquals(AppointmentStatus.PENDING, result.getStatus());
        assertEquals(1, state.saved.size());
        assertEquals(result.getAppointmentId(), publisher.createdEvent.getAppointmentId());
    }

    @Test
    void completedTransitionEnforcesBusinessRule() {
        Appointment a = new Appointment("id-1", "12345", 10, CountryISO.CL);
        a.markCompleted();
        assertEquals(AppointmentStatus.COMPLETED, a.getStatus());
        // Completing twice must fail (idempotency / invariant).
        assertThrows(IllegalStateException.class, a::markCompleted);
    }
}
