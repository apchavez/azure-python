package com.clinic.application.usecases;

import com.clinic.domain.entities.Appointment;
import com.clinic.domain.entities.CountryISO;
import com.clinic.domain.ports.AppointmentEventPublisher;
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

    public CreateAppointmentUseCase(AppointmentStateRepository stateRepository,
                                    AppointmentEventPublisher eventPublisher) {
        this.stateRepository = stateRepository;
        this.eventPublisher = eventPublisher;
    }

    public Appointment execute(String insuredId, int scheduleId, CountryISO countryISO) {
        // UUID generated here (best practice: the request body doesn't carry an id).
        String appointmentId = UUID.randomUUID().toString();

        Appointment appointment = new Appointment(appointmentId, insuredId, scheduleId, countryISO);

        stateRepository.save(appointment);          // PENDING state -> Cosmos
        eventPublisher.publishCreated(appointment); // fan-out -> Service Bus

        return appointment;
    }
}
