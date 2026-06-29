package com.clinic.application.usecases;

import com.clinic.domain.entities.Appointment;
import com.clinic.domain.ports.AppointmentStateRepository;
import com.clinic.domain.shared.Page;

import java.util.List;

/**
 * Application use case: list appointments for an insured.
 * Mirrors the AWS "list appointments by insuredId" query handler.
 */
public class GetAppointmentsUseCase {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final AppointmentStateRepository stateRepository;

    public GetAppointmentsUseCase(AppointmentStateRepository stateRepository) {
        this.stateRepository = stateRepository;
    }

    public List<Appointment> byInsured(String insuredId) {
        return stateRepository.findByInsuredId(insuredId);
    }

    public Page<Appointment> byInsured(String insuredId, int pageSize, String cursor) {
        int size = (pageSize < 1 || pageSize > MAX_PAGE_SIZE) ? DEFAULT_PAGE_SIZE : pageSize;
        return stateRepository.findByInsuredId(insuredId, size, cursor);
    }
}
