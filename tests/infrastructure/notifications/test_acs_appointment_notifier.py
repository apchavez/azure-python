from unittest.mock import MagicMock

from clinic.domain.entities.appointment import Appointment
from clinic.domain.entities.country_iso import CountryISO
from clinic.infrastructure.notifications.acs_appointment_notifier import AcsAppointmentNotifier


def _appointment_with_email():
    a = Appointment.create("a1", "12345", 10, CountryISO.PE)
    a.contact_email = "insured@example.com"
    return a


def test_notify_completed_sends_email():
    client = MagicMock()
    notifier = AcsAppointmentNotifier("https://acs.example.com", "sender@example.com", client)

    notifier.notify_completed(_appointment_with_email())

    client.begin_send.assert_called_once()
    sent_message = client.begin_send.call_args[0][0]
    assert sent_message["senderAddress"] == "sender@example.com"
    assert sent_message["recipients"]["to"][0]["address"] == "insured@example.com"


def test_notify_completed_skips_when_no_email():
    client = MagicMock()
    notifier = AcsAppointmentNotifier("https://acs.example.com", "sender@example.com", client)
    a = Appointment.create("a1", "12345", 10, CountryISO.PE)

    notifier.notify_completed(a)

    client.begin_send.assert_not_called()


def test_notify_cancelled_sends_email():
    client = MagicMock()
    notifier = AcsAppointmentNotifier("https://acs.example.com", "sender@example.com", client)

    notifier.notify_cancelled(_appointment_with_email())

    client.begin_send.assert_called_once()


def test_notify_rescheduled_sends_email_to_old_appointment_contact():
    client = MagicMock()
    notifier = AcsAppointmentNotifier("https://acs.example.com", "sender@example.com", client)
    old = _appointment_with_email()
    new = Appointment.create("a2", "12345", 20, CountryISO.PE)

    notifier.notify_rescheduled(old, new)

    client.begin_send.assert_called_once()


def test_send_failure_is_swallowed_not_propagated():
    client = MagicMock()
    client.begin_send.side_effect = RuntimeError("ACS down")
    notifier = AcsAppointmentNotifier("https://acs.example.com", "sender@example.com", client)

    notifier.notify_completed(_appointment_with_email())  # must not raise
