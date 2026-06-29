package com.clinic.application;

import com.clinic.application.usecases.ProcessAppointmentUseCase;
import com.clinic.domain.entities.Appointment;
import com.clinic.domain.entities.AppointmentEvent;
import com.clinic.domain.entities.AppointmentStatus;
import com.clinic.domain.entities.CountryISO;
import com.clinic.domain.ports.AppointmentEventPublisher;
import com.clinic.domain.ports.AppointmentEventStore;
import com.clinic.domain.ports.AppointmentNotifier;
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
        public void publishCancelled(Appointment a) {}
    }

    static class CapturingNotifier implements AppointmentNotifier {
        Appointment completedNotification;
        public void notifyCompleted(Appointment a) { completedNotification = a; }
        public void notifyCancelled(Appointment a) {}
        public void notifyRescheduled(Appointment o, Appointment n) {}
    }

    static class InMemoryEventStore implements AppointmentEventStore {
        final java.util.List<AppointmentEvent> events = new java.util.ArrayList<>();
        public void append(AppointmentEvent e) { events.add(e); }
        public java.util.List<AppointmentEvent> findByAppointmentId(String id) {
            return events.stream().filter(e -> e.getAppointmentId().equals(id)).toList();
        }
    }

    @Test
    void processesPendingAppointmentToCompletion() {
        InMemoryState state = new InMemoryState();
        CapturingRelational relational = new CapturingRelational();
        CapturingPublisher publisher = new CapturingPublisher();
        CapturingNotifier notifier = new CapturingNotifier();

        Appointment pending = new Appointment("appt-1", "ins-99", 5, CountryISO.PE);
        state.save(pending);

        InMemoryEventStore eventStore = new InMemoryEventStore();
        new ProcessAppointmentUseCase(state, relational, publisher, notifier, eventStore).execute("appt-1");

        assertEquals(AppointmentStatus.COMPLETED, relational.persisted.getStatus());
        assertNotNull(relational.persisted.getCompletedAt());
        assertEquals("appt-1", publisher.completedEvent.getAppointmentId());
        assertEquals("appt-1", notifier.completedNotification.getAppointmentId());
        assertEquals(1, eventStore.events.size());
        assertEquals("APPOINTMENT_COMPLETED", eventStore.events.get(0).getEventType());
    }

    @Test
    void isIdempotentWhenAlreadyCompleted() {
        InMemoryState state = new InMemoryState();
        CapturingRelational relational = new CapturingRelational();
        CapturingPublisher publisher = new CapturingPublisher();

        Appointment completed = new Appointment("appt-2", "ins-99", 5, CountryISO.CL);
        completed.markCompleted();
        state.save(completed);

        new ProcessAppointmentUseCase(state, relational, publisher, new CapturingNotifier(), new InMemoryEventStore()).execute("appt-2");

        assertNull(relational.persisted);
        assertNull(publisher.completedEvent);
    }

    @Test
    void throwsWhenAppointmentNotFound() {
        assertThrows(IllegalStateException.class,
                () -> new ProcessAppointmentUseCase(
                        new InMemoryState(), new CapturingRelational(),
                        new CapturingPublisher(), new CapturingNotifier(), new InMemoryEventStore())
                        .execute("nonexistent"));
    }
}
