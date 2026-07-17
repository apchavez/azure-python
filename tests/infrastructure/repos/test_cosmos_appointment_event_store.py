from unittest.mock import MagicMock

from clinic.domain.entities.appointment import Appointment
from clinic.domain.entities.appointment_event import AppointmentEvent
from clinic.domain.entities.country_iso import CountryISO
from clinic.infrastructure.config.resilience import CircuitBreaker
from clinic.infrastructure.repos.cosmos_appointment_event_store import CosmosAppointmentEventStore


def _make_store():
    container = MagicMock()
    store = CosmosAppointmentEventStore(
        "https://cosmos.example.com",
        "clinicdb",
        "appointment-events",
        CircuitBreaker("test-cosmos-events"),
        container=container,
    )
    return store, container


def test_append_creates_item_with_appointment_id_for_partition_key_derivation():
    store, container = _make_store()
    a = Appointment.create("a1", "12345", 10, CountryISO.PE)
    event = AppointmentEvent.of("APPOINTMENT_CREATED", a)

    store.append(event)

    container.create_item.assert_called_once()
    args, kwargs = container.create_item.call_args
    # create_item derives the partition key from the item body's appointmentId field
    # (the container's configured partition key path) - it must not be passed as a
    # kwarg, since this SDK version raises a TypeError if it is.
    assert "partition_key" not in kwargs
    assert args[0]["appointmentId"] == "a1"


def test_find_by_appointment_id_returns_sorted_events():
    store, container = _make_store()
    container.query_items.return_value = [
        {
            "id": "e2",
            "appointmentId": "a1",
            "eventType": "APPOINTMENT_COMPLETED",
            "insuredId": "12345",
            "scheduleId": 10,
            "countryISO": "PE",
            "status": "COMPLETED",
            "occurredAt": "2026-01-02T00:00:00+00:00",
        },
        {
            "id": "e1",
            "appointmentId": "a1",
            "eventType": "APPOINTMENT_CREATED",
            "insuredId": "12345",
            "scheduleId": 10,
            "countryISO": "PE",
            "status": "PENDING",
            "occurredAt": "2026-01-01T00:00:00+00:00",
        },
    ]

    result = store.find_by_appointment_id("a1")

    assert [e.event_id for e in result] == ["e1", "e2"]
