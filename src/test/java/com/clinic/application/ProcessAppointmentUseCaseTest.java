package com.clinic.application;

import com.clinic.application.usecases.ProcessAppointmentUseCase;
import com.clinic.domain.entities.Appointment;
import com.clinic.domain.entities.AppointmentStatus;
import com.clinic.domain.entities.CountryISO;
import com.clinic.domain.ports.AppointmentEventPublisher;
import com.clinic.domain.ports.AppointmentRelationalRepository;
import com.clinic.domain.ports.AppointmentStateRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProcessAppointmentUseCaseTest {

    static class InMemoryState implements AppointmentStateRepository {
        final List<Appointment> store = new ArrayList<>();

        public void save(Appointment a) { store.add(a); }

        public Optional<Appointment> findById(String id) {
            return store.stream().filter(x -> x.getAppointmentId().equals(id)).findFirst();
        }

        public List<Appointment> findByInsuredId(String insuredId) {
            return store.stream().filter(x -> x.getInsuredId().equals(insuredId)).toList();
        }

        public void updateStatus(Appointment a) {
            store.removeIf(x -> x.getAppointmentId().equals(a.getAppointmentId()));
            store.add(a);
        }
    }

    static class CapturingRelational implements AppointmentRelationalRepository {
        Appointment persisted;
        public void persist(Appointment a) { persisted = a; }
    }

    static class CapturingPublisher implements AppointmentEventPublisher {
        Appointment completedEvent;
        public void publishCreated(Appointment a) {}
        public void publishCompleted(Appointment a) { completedEvent = a; }
    }

    @Test
    void processesPendingAppointmentToCompletion() {
        InMemoryState state = new InMemoryState();
        CapturingRelational relational = new CapturingRelational();
        CapturingPublisher publisher = new CapturingPublisher();

        Appointment pending = new Appointment("appt-1", "ins-99", 5, CountryISO.PE);
        state.save(pending);

        ProcessAppointmentUseCase useCase = new ProcessAppointmentUseCase(state, relational, publisher);
        useCase.execute("appt-1");

        assertEquals(AppointmentStatus.COMPLETED, relational.persisted.getStatus());
        assertNotNull(relational.persisted.getCompletedAt());
        assertEquals("appt-1", publisher.completedEvent.getAppointmentId());
    }

    @Test
    void isIdempotentWhenAlreadyCompleted() {
        InMemoryState state = new InMemoryState();
        CapturingRelational relational = new CapturingRelational();
        CapturingPublisher publisher = new CapturingPublisher();

        Appointment completed = new Appointment("appt-2", "ins-99", 5, CountryISO.CL);
        completed.markCompleted();
        state.save(completed);

        ProcessAppointmentUseCase useCase = new ProcessAppointmentUseCase(state, relational, publisher);
        useCase.execute("appt-2");

        // Must skip silently — relational store and publisher should not be called.
        assertNull(relational.persisted);
        assertNull(publisher.completedEvent);
    }

    @Test
    void throwsWhenAppointmentNotFound() {
        InMemoryState state = new InMemoryState();
        ProcessAppointmentUseCase useCase = new ProcessAppointmentUseCase(
                state, new CapturingRelational(), new CapturingPublisher());

        assertThrows(IllegalStateException.class, () -> useCase.execute("nonexistent"));
    }
}
