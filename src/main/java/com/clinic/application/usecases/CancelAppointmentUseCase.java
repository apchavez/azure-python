package com.clinic.application.usecases;

import com.clinic.domain.entities.Appointment;
import com.clinic.domain.entities.AppointmentEvent;
import com.clinic.domain.ports.AppointmentEventPublisher;
import com.clinic.domain.ports.AppointmentEventStore;
import com.clinic.domain.ports.AppointmentNotifier;
import com.clinic.domain.ports.AppointmentStateRepository;

import java.util.Optional;

public class CancelAppointmentUseCase {

    private final AppointmentStateRepository stateRepository;
    private final AppointmentEventPublisher eventPublisher;
    private final AppointmentNotifier notifier;
    private final AppointmentEventStore eventStore;

    public CancelAppointmentUseCase(AppointmentStateRepository stateRepository,
                                    AppointmentEventPublisher eventPublisher,
                                    AppointmentNotifier notifier,
                                    AppointmentEventStore eventStore) {
        this.stateRepository = stateRepository;
        this.eventPublisher = eventPublisher;
        this.notifier = notifier;
        this.eventStore = eventStore;
    }

    public void execute(String appointmentId) {
        Optional<Appointment> maybe = stateRepository.findById(appointmentId);
        if (maybe.isEmpty()) {
            throw new IllegalStateException("Appointment not found: " + appointmentId);
        }
        Appointment appointment = maybe.get();
        appointment.markCancelled();
        stateRepository.updateStatus(appointment);
        eventPublisher.publishCancelled(appointment);
        eventStore.append(AppointmentEvent.of("APPOINTMENT_CANCELLED", appointment));
        notifier.notifyCancelled(appointment);
    }
}
