package com.clinic.application;

import com.clinic.application.usecases.CancelAppointmentUseCase;
import com.clinic.domain.entities.Appointment;
import com.clinic.domain.entities.AppointmentEvent;
import com.clinic.domain.entities.AppointmentStatus;
import com.clinic.domain.entities.CountryISO;
import com.clinic.domain.ports.AppointmentEventPublisher;
import com.clinic.domain.ports.AppointmentEventStore;
import com.clinic.domain.ports.AppointmentNotifier;
import com.clinic.domain.ports.AppointmentStateRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CancelAppointmentUseCaseTest {

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

    static class CapturingPublisher implements AppointmentEventPublisher {
        Appointment cancelledEvent;
        public void publishCreated(Appointment a) { }
        public void publishCompleted(Appointment a) { }
        public void publishCancelled(Appointment a) { cancelledEvent = a; }
    }

    static class CapturingNotifier implements AppointmentNotifier {
        Appointment cancelledNotification;
        public void notifyCompleted(Appointment a) {}
        public void notifyCancelled(Appointment a) { cancelledNotification = a; }
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
    void cancelsPendingAppointmentAndPublishesEvent() {
        InMemoryState state = new InMemoryState();
        CapturingPublisher publisher = new CapturingPublisher();
        CapturingNotifier notifier = new CapturingNotifier();
        InMemoryEventStore eventStore = new InMemoryEventStore();
        state.save(new Appointment("appt-1", "12345", 10, CountryISO.PE));

        new CancelAppointmentUseCase(state, publisher, notifier, eventStore).execute("appt-1");

        Appointment updated = state.findById("appt-1").orElseThrow();
        assertEquals(AppointmentStatus.CANCELLED, updated.getStatus());
        assertNotNull(updated.getCancelledAt());
        assertEquals("appt-1", publisher.cancelledEvent.getAppointmentId());
        assertEquals("appt-1", notifier.cancelledNotification.getAppointmentId());
        assertEquals(1, eventStore.events.size());
        assertEquals("APPOINTMENT_CANCELLED", eventStore.events.get(0).getEventType());
    }

    @Test
    void throwsWhenAppointmentNotFound() {
        assertThrows(IllegalStateException.class,
                () -> new CancelAppointmentUseCase(
                        new InMemoryState(), new CapturingPublisher(), new CapturingNotifier(), new InMemoryEventStore())
                        .execute("missing"));
    }

    @Test
    void throwsWhenCancellingNonPendingAppointment() {
        InMemoryState state = new InMemoryState();
        Appointment completed = new Appointment("appt-2", "12345", 10, CountryISO.CL);
        completed.markCompleted();
        state.save(completed);

        assertThrows(IllegalStateException.class,
                () -> new CancelAppointmentUseCase(
                        state, new CapturingPublisher(), new CapturingNotifier(), new InMemoryEventStore()).execute("appt-2"));
    }
}
