package com.clinic.domain.ports;

import com.clinic.domain.entities.Appointment;

/**
 * Port for final relational persistence.
 * Implemented by an Azure Database for MySQL adapter
 * (equivalent to MySQL in the AWS project).
 */
public interface AppointmentRelationalRepository {

    void persist(Appointment appointment);
}
