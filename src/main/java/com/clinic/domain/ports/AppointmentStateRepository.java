package com.clinic.domain.ports;

import com.clinic.domain.entities.Appointment;

import java.util.List;
import java.util.Optional;

/**
 * Port (contract) for fast state tracking storage.
 * Implemented by a Cosmos DB adapter in the infrastructure layer
 * (equivalent to DynamoDB in the AWS project).
 *
 * The domain/application layers depend ONLY on this interface, never on Cosmos.
 */
public interface AppointmentStateRepository {

    void save(Appointment appointment);

    Optional<Appointment> findById(String appointmentId);

    List<Appointment> findByInsuredId(String insuredId);

    void updateStatus(Appointment appointment);
}
