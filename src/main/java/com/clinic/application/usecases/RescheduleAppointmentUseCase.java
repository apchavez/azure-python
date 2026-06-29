package com.clinic.application.usecases;

import com.clinic.domain.entities.Appointment;
import com.clinic.domain.ports.AppointmentEventPublisher;
import com.clinic.domain.ports.AppointmentStateRepository;

import java.util.Optional;
import java.util.UUID;

public class RescheduleAppointmentUseCase {

    private final AppointmentStateRepository stateRepository;
    private final AppointmentEventPublisher eventPublisher;

    public RescheduleAppointmentUseCase(AppointmentStateRepository stateRepository,
                                        AppointmentEventPublisher eventPublisher) {
        this.stateRepository = stateRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Marks the existing appointment as RESCHEDULED and creates a new PENDING
     * appointment for the new schedule slot. The new appointment goes through
     * the same event-driven flow as a fresh creation.
     */
    public Appointment execute(String appointmentId, int newScheduleId) {
        Optional<Appointment> maybe = stateRepository.findById(appointmentId);
        if (maybe.isEmpty()) {
            throw new IllegalStateException("Appointment not found: " + appointmentId);
        }
        Appointment old = maybe.get();
        old.markRescheduled();
        stateRepository.updateStatus(old);

        Appointment newAppointment = new Appointment(
                UUID.randomUUID().toString(),
                old.getInsuredId(),
                newScheduleId,
                old.getCountryISO());
        stateRepository.save(newAppointment);
        eventPublisher.publishCreated(newAppointment);

        return newAppointment;
    }
}
