import pytest

from clinic.domain.entities.appointment import Appointment
from clinic.domain.entities.appointment_status import AppointmentStatus
from clinic.domain.entities.country_iso import CountryISO
from clinic.domain.exceptions import IllegalStateError


def _pending():
    return Appointment.create("apt-1", "12345", 10, CountryISO.PE)


def test_create_sets_pending_status_and_created_at():
    a = _pending()
    assert a.status == AppointmentStatus.PENDING
    assert a.created_at is not None
    assert a.appointment_id == "apt-1"
    assert a.insured_id == "12345"
    assert a.schedule_id == 10
    assert a.country_iso == CountryISO.PE


def test_mark_completed_from_pending():
    a = _pending()
    a.mark_completed()
    assert a.status == AppointmentStatus.COMPLETED
    assert a.completed_at is not None


def test_mark_completed_rejects_non_pending():
    a = _pending()
    a.mark_completed()
    with pytest.raises(IllegalStateError):
        a.mark_completed()


def test_mark_cancelled_from_pending():
    a = _pending()
    a.mark_cancelled()
    assert a.status == AppointmentStatus.CANCELLED
    assert a.cancelled_at is not None


def test_mark_cancelled_rejects_non_pending():
    a = _pending()
    a.mark_cancelled()
    with pytest.raises(IllegalStateError):
        a.mark_cancelled()


def test_mark_rescheduled_from_pending():
    a = _pending()
    a.mark_rescheduled()
    assert a.status == AppointmentStatus.RESCHEDULED


def test_mark_rescheduled_rejects_non_pending():
    a = _pending()
    a.mark_rescheduled()
    with pytest.raises(IllegalStateError):
        a.mark_rescheduled()
