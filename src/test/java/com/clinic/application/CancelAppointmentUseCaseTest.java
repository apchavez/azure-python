package com.clinic.application;

import com.clinic.application.usecases.CancelAppointmentUseCase;
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

    @Test
    void cancelsPendingAppointmentAndPublishesEvent() {
        InMemoryState state = new InMemoryState();
        CapturingPublisher publisher = new CapturingPublisher();
        state.save(new Appointment("appt-1", "12345", 10, CountryISO.PE));

        new CancelAppointmentUseCase(state, publisher).execute("appt-1");

        Appointment updated = state.findById("appt-1").orElseThrow();
        assertEquals(AppointmentStatus.CANCELLED, updated.getStatus());
        assertNotNull(updated.getCancelledAt());
        assertEquals("appt-1", publisher.cancelledEvent.getAppointmentId());
    }

    @Test
    void throwsWhenAppointmentNotFound() {
        assertThrows(IllegalStateException.class,
                () -> new CancelAppointmentUseCase(new InMemoryState(), new CapturingPublisher())
                        .execute("missing"));
    }

    @Test
    void throwsWhenCancellingNonPendingAppointment() {
        InMemoryState state = new InMemoryState();
        Appointment completed = new Appointment("appt-2", "12345", 10, CountryISO.CL);
        completed.markCompleted();
        state.save(completed);

        assertThrows(IllegalStateException.class,
                () -> new CancelAppointmentUseCase(state, new CapturingPublisher()).execute("appt-2"));
    }
}
