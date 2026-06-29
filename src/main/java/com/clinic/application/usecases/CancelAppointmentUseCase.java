package com.clinic.application.usecases;

import com.clinic.domain.entities.Appointment;
import com.clinic.domain.ports.AppointmentEventPublisher;
import com.clinic.domain.ports.AppointmentStateRepository;

import java.util.Optional;

public class CancelAppointmentUseCase {

    private final AppointmentStateRepository stateRepository;
    private final AppointmentEventPublisher eventPublisher;

    public CancelAppointmentUseCase(AppointmentStateRepository stateRepository,
                                    AppointmentEventPublisher eventPublisher) {
        this.stateRepository = stateRepository;
        this.eventPublisher = eventPublisher;
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
    }
}
