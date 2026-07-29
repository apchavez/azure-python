import pytest

from clinic.application.usecases.process_appointment import ProcessAppointmentUseCase
from clinic.domain.entities.appointment import Appointment
from clinic.domain.entities.appointment_status import AppointmentStatus
from clinic.domain.exceptions import IllegalStateError
from tests.fakes import (
    CapturingConfirmationPublisher,
    CapturingRelationalRepository,
    InMemoryStateRepository,
)


def test_persists_relational_record_and_publishes_confirmation():
    state = InMemoryStateRepository()
    relational = CapturingRelationalRepository()
    confirmation_publisher = CapturingConfirmationPublisher()
    appt = Appointment.create("appt-1", "12345", 10)
    state.save(appt)

    ProcessAppointmentUseCase(state, relational, confirmation_publisher).execute("appt-1")

    assert len(relational.persisted) == 1
    assert relational.persisted[0].status == AppointmentStatus.COMPLETED
    assert confirmation_publisher.confirmed == ["appt-1"]
    # Note: this use case never calls state_repository.update_status() - Cosmos completion is
    # ConfirmAppointmentUseCase's job. InMemoryStateRepository.find_by_id() returns the same
    # object reference find_by_id fetched (unlike a real Cosmos round-trip, which deserializes a
    # fresh copy each time), so mark_completed()'s in-memory mutation above is visible through
    # `state` too - that's a fake-repository artifact, not something this use case does for real.


def test_is_idempotent_for_already_completed_appointment():
    state = InMemoryStateRepository()
    relational = CapturingRelationalRepository()
    confirmation_publisher = CapturingConfirmationPublisher()
    appt = Appointment.create("appt-1", "12345", 10)
    appt.mark_completed()
    state.save(appt)

    ProcessAppointmentUseCase(state, relational, confirmation_publisher).execute("appt-1")

    assert relational.persisted == []
    assert confirmation_publisher.confirmed == []


def test_throws_when_appointment_not_found():
    state = InMemoryStateRepository()
    with pytest.raises(IllegalStateError):
        ProcessAppointmentUseCase(
            state,
            CapturingRelationalRepository(),
            CapturingConfirmationPublisher(),
        ).execute("missing")
