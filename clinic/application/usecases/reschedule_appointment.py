from __future__ import annotations

import uuid

from clinic.domain.entities.appointment import Appointment
from clinic.domain.entities.appointment_event import AppointmentEvent
from clinic.domain.exceptions import ForbiddenError, IllegalStateError
from clinic.domain.ports.appointment_event_publisher import AppointmentEventPublisher
from clinic.domain.ports.appointment_event_store import AppointmentEventStore
from clinic.domain.ports.appointment_notifier import AppointmentNotifier
from clinic.domain.ports.appointment_state_repository import AppointmentStateRepository
from clinic.infrastructure.auth.jwt_validator import AuthenticatedUser


class RescheduleAppointmentUseCase:
    def __init__(
        self,
        state_repository: AppointmentStateRepository,
        event_publisher: AppointmentEventPublisher,
        notifier: AppointmentNotifier,
        event_store: AppointmentEventStore,
    ) -> None:
        self._state_repository = state_repository
        self._event_publisher = event_publisher
        self._notifier = notifier
        self._event_store = event_store

    def execute(
        self, appointment_id: str, new_schedule_id: int, requesting_user: AuthenticatedUser
    ) -> Appointment:
        """Marks the existing appointment as RESCHEDULED and creates a new PENDING appointment for
        the new schedule slot. The new appointment goes through the same event-driven flow as a
        fresh creation.
        """
        old = self._state_repository.find_by_id(appointment_id)
        if old is None:
            raise IllegalStateError(f"Appointment not found: {appointment_id}")
        if requesting_user.role == "insured" and old.insured_id != requesting_user.sub:
            raise ForbiddenError("insured can only reschedule their own appointments")

        old.mark_rescheduled()
        self._state_repository.update_status(old)

        new_appointment = Appointment.create(
            str(uuid.uuid4()), old.insured_id, new_schedule_id, old.country_iso
        )
        new_appointment.contact_email = old.contact_email
        self._state_repository.save(new_appointment)
        self._event_publisher.publish_created(new_appointment)
        self._event_store.append(AppointmentEvent.of("APPOINTMENT_RESCHEDULED", old))
        self._event_store.append(AppointmentEvent.of("APPOINTMENT_CREATED", new_appointment))
        self._notifier.notify_rescheduled(old, new_appointment)

        return new_appointment
