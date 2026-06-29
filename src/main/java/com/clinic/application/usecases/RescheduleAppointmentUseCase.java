package com.clinic.application.usecases;

import com.clinic.domain.entities.Appointment;
import com.clinic.domain.entities.AppointmentEvent;
import com.clinic.domain.ports.AppointmentEventPublisher;
import com.clinic.domain.ports.AppointmentEventStore;
import com.clinic.domain.ports.AppointmentNotifier;
import com.clinic.domain.ports.AppointmentStateRepository;

import java.util.Optional;
import java.util.UUID;

public class RescheduleAppointmentUseCase {

    private final AppointmentStateRepository stateRepository;
    private final AppointmentEventPublisher eventPublisher;
    private final AppointmentNotifier notifier;
    private final AppointmentEventStore eventStore;

    public RescheduleAppointmentUseCase(AppointmentStateRepository stateRepository,
                                        AppointmentEventPublisher eventPublisher,
                                        AppointmentNotifier notifier,
                                        AppointmentEventStore eventStore) {
        this.stateRepository = stateRepository;
        this.eventPublisher = eventPublisher;
        this.notifier = notifier;
        this.eventStore = eventStore;
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
        newAppointment.setContactEmail(old.getContactEmail());
        stateRepository.save(newAppointment);
        eventPublisher.publishCreated(newAppointment);
        eventStore.append(AppointmentEvent.of("APPOINTMENT_RESCHEDULED", old));
        eventStore.append(AppointmentEvent.of("APPOINTMENT_CREATED", newAppointment));
        notifier.notifyRescheduled(old, newAppointment);

        return newAppointment;
    }
}
