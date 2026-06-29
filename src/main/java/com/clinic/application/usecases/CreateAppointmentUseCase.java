package com.clinic.application.usecases;

import com.clinic.domain.entities.Appointment;
import com.clinic.domain.entities.AppointmentEvent;
import com.clinic.domain.entities.CountryISO;
import com.clinic.domain.ports.AppointmentEventPublisher;
import com.clinic.domain.ports.AppointmentEventStore;
import com.clinic.domain.ports.AppointmentStateRepository;

import java.util.UUID;

/**
 * Application use case: create a new appointment request.
 *
 * Mirrors the AWS "createAppointment" handler logic, but keeps the business
 * orchestration framework-agnostic. Depends only on ports (interfaces).
 *
 * Flow: generate id -> persist PENDING state (Cosmos) -> publish created event
 * (Service Bus) for country-specific processing.
 */
public class CreateAppointmentUseCase {

    private final AppointmentStateRepository stateRepository;
    private final AppointmentEventPublisher eventPublisher;
    private final AppointmentEventStore eventStore;

    public CreateAppointmentUseCase(AppointmentStateRepository stateRepository,
                                    AppointmentEventPublisher eventPublisher,
                                    AppointmentEventStore eventStore) {
        this.stateRepository = stateRepository;
        this.eventPublisher = eventPublisher;
        this.eventStore = eventStore;
    }

    public Appointment execute(String insuredId, int scheduleId, CountryISO countryISO, String contactEmail) {
        String appointmentId = UUID.randomUUID().toString();
        Appointment appointment = new Appointment(appointmentId, insuredId, scheduleId, countryISO);
        appointment.setContactEmail(contactEmail);

        stateRepository.save(appointment);
        eventPublisher.publishCreated(appointment);
        eventStore.append(AppointmentEvent.of("APPOINTMENT_CREATED", appointment));

        return appointment;
    }
}
