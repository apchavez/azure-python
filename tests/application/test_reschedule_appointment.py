import pytest

from clinic.application.usecases.reschedule_appointment import RescheduleAppointmentUseCase
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


def test_marks_old_as_rescheduled_and_creates_new_pending_appointment():
    state = InMemoryStateRepository()
    publisher = CapturingEventPublisher()
    notifier = CapturingNotifier()
    event_store = InMemoryEventStore()
    original = Appointment.create("old-id", "12345", 10, CountryISO.PE)
    original.contact_email = "test@example.com"
    state.save(original)

    new_appt = RescheduleAppointmentUseCase(state, publisher, notifier, event_store).execute(
        "old-id", 99, AGENT
    )

    old = state.find_by_id("old-id")
    assert old.status == AppointmentStatus.RESCHEDULED
    assert new_appt.appointment_id != "old-id"
    assert new_appt.status == AppointmentStatus.PENDING
    assert new_appt.schedule_id == 99
    assert new_appt.insured_id == "12345"
    assert new_appt.country_iso == CountryISO.PE
    assert new_appt.contact_email == "test@example.com"
    assert publisher.created == [new_appt]
    assert notifier.rescheduled == [(old, new_appt)]
    assert len(event_store.events) == 2
    assert event_store.events[0].event_type == "APPOINTMENT_RESCHEDULED"
    assert event_store.events[1].event_type == "APPOINTMENT_CREATED"


def test_throws_when_appointment_not_found():
    state = InMemoryStateRepository()
    with pytest.raises(IllegalStateError):
        RescheduleAppointmentUseCase(
            state, CapturingEventPublisher(), CapturingNotifier(), InMemoryEventStore()
        ).execute("missing", 10, AGENT)


def test_throws_when_rescheduling_non_pending_appointment():
    state = InMemoryStateRepository()
    completed = Appointment.create("appt-2", "12345", 10, CountryISO.CL)
    completed.mark_completed()
    state.save(completed)

    with pytest.raises(IllegalStateError):
        RescheduleAppointmentUseCase(
            state, CapturingEventPublisher(), CapturingNotifier(), InMemoryEventStore()
        ).execute("appt-2", 20, AGENT)


def test_insured_can_reschedule_their_own_appointment():
    state = InMemoryStateRepository()
    state.save(Appointment.create("appt-3", "12345", 10, CountryISO.PE))

    new_appt = RescheduleAppointmentUseCase(
        state, CapturingEventPublisher(), CapturingNotifier(), InMemoryEventStore()
    ).execute("appt-3", 20, OWNER)

    assert new_appt.schedule_id == 20


def test_insured_cannot_reschedule_someone_elses_appointment():
    state = InMemoryStateRepository()
    state.save(Appointment.create("appt-4", "12345", 10, CountryISO.PE))

    with pytest.raises(ForbiddenError):
        RescheduleAppointmentUseCase(
            state, CapturingEventPublisher(), CapturingNotifier(), InMemoryEventStore()
        ).execute("appt-4", 20, OTHER_INSURED)
