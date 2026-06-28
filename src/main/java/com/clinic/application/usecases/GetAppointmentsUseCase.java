package com.clinic.application.usecases;

import com.clinic.domain.entities.Appointment;
import com.clinic.domain.ports.AppointmentStateRepository;

import java.util.List;

/**
 * Application use case: list appointments for an insured.
 * Mirrors the AWS "list appointments by insuredId" query handler.
 */
public class GetAppointmentsUseCase {

    private final AppointmentStateRepository stateRepository;

    public GetAppointmentsUseCase(AppointmentStateRepository stateRepository) {
        this.stateRepository = stateRepository;
    }

    public List<Appointment> byInsured(String insuredId) {
        return stateRepository.findByInsuredId(insuredId);
    }
}
