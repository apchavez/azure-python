import pytest

from clinic.application.usecases.cancel_appointment import CancelAppointmentUseCase
from clinic.domain.entities.appointment import Appointment
from clinic.domain.entities.appointment_status import AppointmentStatus
from clinic.domain.entities.country_iso import CountryISO
from clinic.domain.exceptions import ForbiddenError, IllegalStateError
from clinic.infrastructure.auth.jwt_validator import AuthenticatedUser
from tests.fakes import (
    CapturingEventPublisher,
    CapturingNotifier,
    InMemoryEventStore,
    InMemoryStateRepository,
)

AGENT = AuthenticatedUser(sub="agent-1", role="agent")
OWNER = AuthenticatedUser(sub="12345", role="insured")
OTHER_INSURED = AuthenticatedUser(sub="99999", role="insured")


def _seed(state: InMemoryStateRepository, appointment_id="appt-1", insured_id="12345"):
    a = Appointment.create(appointment_id, insured_id, 10, CountryISO.PE)
    state.save(a)
    return a


def test_cancels_pending_appointment_and_publishes_event():
    state = InMemoryStateRepository()
    publisher = CapturingEventPublisher()
    notifier = CapturingNotifier()
    event_store = InMemoryEventStore()
    _seed(state)

    CancelAppointmentUseCase(state, publisher, notifier, event_store).execute("appt-1", AGENT)

    updated = state.find_by_id("appt-1")
    assert updated.status == AppointmentStatus.CANCELLED
    assert updated.cancelled_at is not None
    assert publisher.cancelled == [updated]
    assert notifier.cancelled == [updated]
    assert len(event_store.events) == 1
    assert event_store.events[0].event_type == "APPOINTMENT_CANCELLED"


def test_throws_when_appointment_not_found():
    state = InMemoryStateRepository()
    with pytest.raises(IllegalStateError):
        CancelAppointmentUseCase(
            state, CapturingEventPublisher(), CapturingNotifier(), InMemoryEventStore()
        ).execute("missing", AGENT)


def test_throws_when_cancelling_non_pending_appointment():
    state = InMemoryStateRepository()
    a = _seed(state, "appt-2")
    a.mark_completed()

    with pytest.raises(IllegalStateError):
        CancelAppointmentUseCase(
            state, CapturingEventPublisher(), CapturingNotifier(), InMemoryEventStore()
        ).execute("appt-2", AGENT)


def test_insured_can_cancel_their_own_appointment():
    state = InMemoryStateRepository()
    _seed(state, "appt-3")

    CancelAppointmentUseCase(
        state, CapturingEventPublisher(), CapturingNotifier(), InMemoryEventStore()
    ).execute("appt-3", OWNER)

    assert state.find_by_id("appt-3").status == AppointmentStatus.CANCELLED


def test_insured_cannot_cancel_someone_elses_appointment():
    state = InMemoryStateRepository()
    _seed(state, "appt-4")

    with pytest.raises(ForbiddenError):
        CancelAppointmentUseCase(
            state, CapturingEventPublisher(), CapturingNotifier(), InMemoryEventStore()
        ).execute("appt-4", OTHER_INSURED)
