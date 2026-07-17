from unittest.mock import MagicMock

from azure.core.exceptions import ResourceNotFoundError

from clinic.domain.entities.appointment import Appointment
from clinic.domain.entities.country_iso import CountryISO
from clinic.infrastructure.config.resilience import CircuitBreaker
from clinic.infrastructure.repos.cosmos_appointment_state_repository import (
    CosmosAppointmentStateRepository,
)


def _make_repo():
    container = MagicMock()
    repo = CosmosAppointmentStateRepository(
        "https://cosmos.example.com",
        "clinicdb",
        "appointments",
        CircuitBreaker("test-cosmos"),
        container=container,
    )
    return repo, container


def test_save_creates_item():
    repo, container = _make_repo()
    a = Appointment.create("a1", "12345", 10, CountryISO.PE)

    repo.save(a)

    container.create_item.assert_called_once()
    item = container.create_item.call_args[0][0]
    assert item["id"] == "a1"
    assert item["countryISO"] == "PE"


def test_find_by_id_returns_appointment_when_found():
    repo, container = _make_repo()
    container.read_item.return_value = {
        "id": "a1",
        "insuredId": "12345",
        "scheduleId": 10,
        "countryISO": "PE",
        "status": "PENDING",
        "createdAt": "2026-01-01T00:00:00+00:00",
        "completedAt": None,
        "cancelledAt": None,
        "contactEmail": None,
    }

    result = repo.find_by_id("a1")

    assert result is not None
    assert result.appointment_id == "a1"
    assert result.country_iso == CountryISO.PE


def test_find_by_id_returns_none_when_not_found():
    repo, container = _make_repo()
    container.read_item.side_effect = ResourceNotFoundError("not found")

    assert repo.find_by_id("missing") is None


def test_update_status_replaces_item():
    repo, container = _make_repo()
    a = Appointment.create("a1", "12345", 10, CountryISO.PE)
    a.mark_cancelled()

    repo.update_status(a)

    container.replace_item.assert_called_once()


def test_ping_up_and_down():
    repo, container = _make_repo()
    assert repo.ping() == "UP"

    container.read.side_effect = RuntimeError("down")
    assert repo.ping().startswith("DOWN")
