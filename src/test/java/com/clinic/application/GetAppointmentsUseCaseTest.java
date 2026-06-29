package com.clinic.application;

import com.clinic.application.usecases.CreateAppointmentUseCase;
import com.clinic.application.usecases.GetAppointmentsUseCase;
import com.clinic.domain.entities.Appointment;
import com.clinic.domain.entities.AppointmentEvent;
import com.clinic.domain.entities.CountryISO;
import com.clinic.domain.ports.AppointmentEventPublisher;
import com.clinic.domain.ports.AppointmentEventStore;
import com.clinic.domain.ports.AppointmentStateRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GetAppointmentsUseCaseTest {

    static class InMemoryState implements AppointmentStateRepository {
        final List<Appointment> store = new ArrayList<>();
        public void save(Appointment a) { store.add(a); }
        public Optional<Appointment> findById(String id) {
            return store.stream().filter(x -> x.getAppointmentId().equals(id)).findFirst();
        }
        public List<Appointment> findByInsuredId(String insuredId) {
            return store.stream().filter(x -> x.getInsuredId().equals(insuredId)).toList();
        }
        public void updateStatus(Appointment a) {}
    }

    static class NoOpPublisher implements AppointmentEventPublisher {
        public void publishCreated(Appointment a) {}
        public void publishCompleted(Appointment a) {}
        public void publishCancelled(Appointment a) {}
    }

    static class NoOpEventStore implements AppointmentEventStore {
        public void append(AppointmentEvent e) {}
        public java.util.List<AppointmentEvent> findByAppointmentId(String id) { return java.util.List.of(); }
    }

    @Test
    void returnsAllAppointmentsForInsured() {
        InMemoryState state = new InMemoryState();
        CreateAppointmentUseCase create = new CreateAppointmentUseCase(state, new NoOpPublisher(), new NoOpEventStore());
        GetAppointmentsUseCase query = new GetAppointmentsUseCase(state);

        create.execute("ins-10", 1, CountryISO.PE, null);
        create.execute("ins-10", 2, CountryISO.CL, null);
        create.execute("ins-99", 3, CountryISO.PE, null);

        List<Appointment> results = query.byInsured("ins-10");
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(a -> "ins-10".equals(a.getInsuredId())));
    }

    @Test
    void returnsEmptyListWhenNoAppointmentsExist() {
        InMemoryState state = new InMemoryState();
        GetAppointmentsUseCase query = new GetAppointmentsUseCase(state);

        List<Appointment> results = query.byInsured("unknown");
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
}
