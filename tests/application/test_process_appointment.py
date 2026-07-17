import pytest

from clinic.application.usecases.process_appointment import ProcessAppointmentUseCase
from clinic.domain.entities.appointment import Appointment
from clinic.domain.entities.appointment_status import AppointmentStatus
from clinic.domain.entities.country_iso import CountryISO
from clinic.domain.exceptions import IllegalStateError
from tests.fakes import (
    CapturingEventPublisher,
    CapturingNotifier,
    CapturingRelationalRepository,
    InMemoryEventStore,
    InMemoryStateRepository,
)


def test_processes_pending_appointment():
    state = InMemoryStateRepository()
    relational = CapturingRelationalRepository()
    publisher = CapturingEventPublisher()
    notifier = CapturingNotifier()
    event_store = InMemoryEventStore()
    appt = Appointment.create("appt-1", "12345", 10, CountryISO.PE)
    state.save(appt)

    ProcessAppointmentUseCase(state, relational, publisher, notifier, event_store).execute("appt-1")

    updated = state.find_by_id("appt-1")
    assert updated.status == AppointmentStatus.COMPLETED
    assert relational.persisted == [updated]
    assert publisher.completed == [updated]
    assert notifier.completed == [updated]
    assert len(event_store.events) == 1
    assert event_store.events[0].event_type == "APPOINTMENT_COMPLETED"


def test_is_idempotent_for_already_completed_appointment():
    state = InMemoryStateRepository()
    relational = CapturingRelationalRepository()
    publisher = CapturingEventPublisher()
    notifier = CapturingNotifier()
    event_store = InMemoryEventStore()
    appt = Appointment.create("appt-1", "12345", 10, CountryISO.PE)
    appt.mark_completed()
    state.save(appt)

    ProcessAppointmentUseCase(state, relational, publisher, notifier, event_store).execute("appt-1")

    assert relational.persisted == []
    assert publisher.completed == []
    assert notifier.completed == []
    assert event_store.events == []


def test_throws_when_appointment_not_found():
    state = InMemoryStateRepository()
    with pytest.raises(IllegalStateError):
        ProcessAppointmentUseCase(
            state,
            CapturingRelationalRepository(),
            CapturingEventPublisher(),
            CapturingNotifier(),
            InMemoryEventStore(),
        ).execute("missing")
