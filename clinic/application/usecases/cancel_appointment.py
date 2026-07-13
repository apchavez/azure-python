from __future__ import annotations

from clinic.domain.entities.appointment_event import AppointmentEvent
from clinic.domain.exceptions import ForbiddenError, IllegalStateError
from clinic.domain.ports.appointment_event_publisher import AppointmentEventPublisher
from clinic.domain.ports.appointment_event_store import AppointmentEventStore
from clinic.domain.ports.appointment_notifier import AppointmentNotifier
from clinic.domain.ports.appointment_state_repository import AppointmentStateRepository
from clinic.infrastructure.auth.jwt_validator import AuthenticatedUser


class CancelAppointmentUseCase:
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

    def execute(self, appointment_id: str, requesting_user: AuthenticatedUser) -> None:
        appointment = self._state_repository.find_by_id(appointment_id)
        if appointment is None:
            raise IllegalStateError(f"Appointment not found: {appointment_id}")
        if requesting_user.role == "insured" and appointment.insured_id != requesting_user.sub:
            raise ForbiddenError("insured can only cancel their own appointments")

        appointment.mark_cancelled()
        self._state_repository.update_status(appointment)
        self._event_publisher.publish_cancelled(appointment)
        self._event_store.append(AppointmentEvent.of("APPOINTMENT_CANCELLED", appointment))
        self._notifier.notify_cancelled(appointment)
