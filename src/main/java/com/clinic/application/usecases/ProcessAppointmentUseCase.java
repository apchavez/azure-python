package com.clinic.application.usecases;

import com.clinic.domain.entities.Appointment;
import com.clinic.domain.entities.AppointmentStatus;
import com.clinic.domain.ports.AppointmentEventPublisher;
import com.clinic.domain.ports.AppointmentRelationalRepository;
import com.clinic.domain.ports.AppointmentStateRepository;

import java.util.Optional;

/**
 * Application use case executed by the country workers (PE/CL).
 * Mirrors the AWS worker Lambda triggered from SQS:
 *   1. load the appointment state (Cosmos)
 *   2. mark it completed (domain invariant)
 *   3. persist the final record (MySQL)
 *   4. update state to completed (Cosmos)
 *   5. publish the completed event (Event Grid)
 *
 * Idempotent: markCompleted() only transitions from PENDING, so a redelivered
 * Service Bus message won't double-process (at-least-once safe).
 */
public class ProcessAppointmentUseCase {

    private final AppointmentStateRepository stateRepository;
    private final AppointmentRelationalRepository relationalRepository;
    private final AppointmentEventPublisher eventPublisher;

    public ProcessAppointmentUseCase(AppointmentStateRepository stateRepository,
                                     AppointmentRelationalRepository relationalRepository,
                                     AppointmentEventPublisher eventPublisher) {
        this.stateRepository = stateRepository;
        this.relationalRepository = relationalRepository;
        this.eventPublisher = eventPublisher;
    }

    public void execute(String appointmentId) {
        Optional<Appointment> maybe = stateRepository.findById(appointmentId);
        if (maybe.isEmpty()) {
            throw new IllegalStateException("Appointment not found: " + appointmentId);
        }
        Appointment appointment = maybe.get();

        // Idempotency guard: if already completed, skip silently.
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            return;
        }

        appointment.markCompleted();              // domain rule
        relationalRepository.persist(appointment); // MySQL (final store)
        stateRepository.updateStatus(appointment); // Cosmos (state -> completed)
        eventPublisher.publishCompleted(appointment); // Event Grid
    }
}
