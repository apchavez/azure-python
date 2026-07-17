"""Application use case executed by the country workers (PE/CL). Mirrors the AWS worker Lambda
triggered from SQS:
1. load the appointment state (Cosmos)
2. mark it completed (domain invariant)
3. persist the final record (Azure SQL)
4. update state to completed (Cosmos)
5. publish the completed event

Idempotent: mark_completed() only transitions from PENDING, so a redelivered Service Bus message
won't double-process (at-least-once safe).
"""

from __future__ import annotations

from clinic.domain.entities.appointment_event import AppointmentEvent
from clinic.domain.entities.appointment_status import AppointmentStatus
from clinic.domain.exceptions import IllegalStateError
from clinic.domain.ports.appointment_event_publisher import AppointmentEventPublisher
from clinic.domain.ports.appointment_event_store import AppointmentEventStore
from clinic.domain.ports.appointment_notifier import AppointmentNotifier
from clinic.domain.ports.appointment_relational_repository import AppointmentRelationalRepository
from clinic.domain.ports.appointment_state_repository import AppointmentStateRepository


class ProcessAppointmentUseCase:
    def __init__(
        self,
        state_repository: AppointmentStateRepository,
        relational_repository: AppointmentRelationalRepository,
        event_publisher: AppointmentEventPublisher,
        notifier: AppointmentNotifier,
        event_store: AppointmentEventStore,
    ) -> None:
        self._state_repository = state_repository
        self._relational_repository = relational_repository
        self._event_publisher = event_publisher
        self._notifier = notifier
        self._event_store = event_store

    def execute(self, appointment_id: str) -> None:
        appointment = self._state_repository.find_by_id(appointment_id)
        if appointment is None:
            raise IllegalStateError(f"Appointment not found: {appointment_id}")

        # Idempotency guard: if already completed, skip silently.
        if appointment.status == AppointmentStatus.COMPLETED:
            return

        appointment.mark_completed()
        self._relational_repository.persist(appointment)
        self._state_repository.update_status(appointment)
        self._event_publisher.publish_completed(appointment)
        self._event_store.append(AppointmentEvent.of("APPOINTMENT_COMPLETED", appointment))
        self._notifier.notify_completed(appointment)
