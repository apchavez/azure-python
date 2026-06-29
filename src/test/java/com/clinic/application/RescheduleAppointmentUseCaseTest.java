package com.clinic.application;

import com.clinic.application.usecases.RescheduleAppointmentUseCase;
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

class RescheduleAppointmentUseCaseTest {

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
        Appointment createdEvent;
        public void publishCreated(Appointment a) { createdEvent = a; }
        public void publishCompleted(Appointment a) { }
        public void publishCancelled(Appointment a) { }
    }

    static class CapturingNotifier implements AppointmentNotifier {
        Appointment rescheduledOld;
        Appointment rescheduledNew;
        public void notifyCompleted(Appointment a) {}
        public void notifyCancelled(Appointment a) {}
        public void notifyRescheduled(Appointment o, Appointment n) { rescheduledOld = o; rescheduledNew = n; }
    }

    static class InMemoryEventStore implements AppointmentEventStore {
        final java.util.List<AppointmentEvent> events = new java.util.ArrayList<>();
        public void append(AppointmentEvent e) { events.add(e); }
        public java.util.List<AppointmentEvent> findByAppointmentId(String id) {
            return events.stream().filter(e -> e.getAppointmentId().equals(id)).toList();
        }
    }

    @Test
    void marksOldAsRescheduledAndCreatesNewPendingAppointment() {
        InMemoryState state = new InMemoryState();
        CapturingPublisher publisher = new CapturingPublisher();
        CapturingNotifier notifier = new CapturingNotifier();
        InMemoryEventStore eventStore = new InMemoryEventStore();
        Appointment original = new Appointment("old-id", "12345", 10, CountryISO.PE);
        original.setContactEmail("test@example.com");
        state.save(original);

        Appointment newAppt = new RescheduleAppointmentUseCase(state, publisher, notifier, eventStore).execute("old-id", 99);

        Appointment old = state.findById("old-id").orElseThrow();
        assertEquals(AppointmentStatus.RESCHEDULED, old.getStatus());

        assertNotEquals("old-id", newAppt.getAppointmentId());
        assertEquals(AppointmentStatus.PENDING, newAppt.getStatus());
        assertEquals(99, newAppt.getScheduleId());
        assertEquals("12345", newAppt.getInsuredId());
        assertEquals(CountryISO.PE, newAppt.getCountryISO());
        assertEquals("test@example.com", newAppt.getContactEmail());
        assertEquals(newAppt.getAppointmentId(), publisher.createdEvent.getAppointmentId());
        assertEquals("old-id", notifier.rescheduledOld.getAppointmentId());
        assertEquals(newAppt.getAppointmentId(), notifier.rescheduledNew.getAppointmentId());
        assertEquals(2, eventStore.events.size());
        assertEquals("APPOINTMENT_RESCHEDULED", eventStore.events.get(0).getEventType());
        assertEquals("APPOINTMENT_CREATED", eventStore.events.get(1).getEventType());
    }

    @Test
    void throwsWhenAppointmentNotFound() {
        assertThrows(IllegalStateException.class,
                () -> new RescheduleAppointmentUseCase(
                        new InMemoryState(), new CapturingPublisher(), new CapturingNotifier(), new InMemoryEventStore())
                        .execute("missing", 10));
    }

    @Test
    void throwsWhenReschedulingNonPendingAppointment() {
        InMemoryState state = new InMemoryState();
        Appointment completed = new Appointment("appt-2", "12345", 10, CountryISO.CL);
        completed.markCompleted();
        state.save(completed);

        assertThrows(IllegalStateException.class,
                () -> new RescheduleAppointmentUseCase(
                        state, new CapturingPublisher(), new CapturingNotifier(), new InMemoryEventStore()).execute("appt-2", 20));
    }
}
